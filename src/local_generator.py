from __future__ import annotations

import json
from pathlib import Path
import re
from threading import Lock
from typing import Sequence

from .embeddings import find_cached_snapshot
from .finetuning import FINETUNED_REFUSAL_MESSAGE, build_finetuning_system_prompt
from .rag_pipeline import OUT_OF_SCOPE_MESSAGE
from .storage import RetrievedChunk


QWEN_CHAT_TEMPLATE = (
    "{% for message in messages %}"
    "{{ '<|im_start|>' + message['role'] + '\\n' + message['content'] + '<|im_end|>\\n' }}"
    "{% endfor %}"
    "{% if add_generation_prompt %}{{ '<|im_start|>assistant\\n' }}{% endif %}"
)


def ensure_chat_template(tokenizer) -> None:
    if not getattr(tokenizer, "chat_template", None):
        tokenizer.chat_template = QWEN_CHAT_TEMPLATE


class LocalLoraGenerator:
    """Single-GPU LoRA inference with bounded prompts and adaptive batching."""

    def __init__(
        self,
        base_model: str,
        adapter_dir: Path,
        cache_dir: Path,
        max_new_tokens: int = 180,
        enforce_quality_gate: bool = True,
    ) -> None:
        import torch
        from peft import PeftModel
        from transformers import AutoModelForCausalLM, AutoTokenizer

        if not adapter_dir.exists():
            raise FileNotFoundError(f"Không tìm thấy LoRA adapter: {adapter_dir}")
        manifest_path = adapter_dir / "training_manifest.json"
        if enforce_quality_gate and not manifest_path.exists():
            raise RuntimeError("LoRA adapter chưa có training_manifest.json nên chưa được xác minh chất lượng.")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
        if enforce_quality_gate and not bool((manifest.get("quality_gate") or {}).get("passed")):
            raise RuntimeError("LoRA adapter không đạt quality gate và bị chặn để tránh trả lời sai.")

        manifest_base_model = str(manifest.get("base_model") or "").strip()
        if manifest_base_model and manifest_base_model != base_model:
            raise RuntimeError(
                "Base model does not match the adapter: "
                f"the manifest requires {manifest_base_model}, but runtime is configured for {base_model}."
            )

        self.base_model = base_model
        self.adapter_dir = adapter_dir
        self.max_new_tokens = max_new_tokens
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self._inference_lock = Lock()
        self._warmed_up = False

        self.tokenizer = AutoTokenizer.from_pretrained(adapter_dir, local_files_only=True)
        ensure_chat_template(self.tokenizer)
        if self.tokenizer.pad_token_id is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
        self.tokenizer.padding_side = "left"

        base = self._load_base_model(AutoModelForCausalLM, torch, base_model, cache_dir)
        adapter = PeftModel.from_pretrained(base, adapter_dir)
        # Benchmark inference never switches adapters. Merging removes PEFT dispatch
        # overhead and keeps just one compact model in memory.
        self.model = adapter.merge_and_unload()
        self.model.to(self.device)
        self.model.eval()

    @property
    def warmed_up(self) -> bool:
        return self._warmed_up

    @property
    def adapter_version(self) -> str:
        return self.adapter_dir.name

    def _load_base_model(self, model_cls, torch_module, base_model: str, cache_dir: Path):
        # This project is deployed on the same CPU that successfully trains in BF16.
        # Keeping inference in BF16 prevents the 1.5B model from exhausting RAM once
        # the Java and frontend services are running alongside Python.
        dtype = torch_module.float16 if self.device == "cuda" else torch_module.bfloat16
        local_base = find_cached_snapshot(cache_dir, base_model, "config.json")
        if local_base:
            return model_cls.from_pretrained(
                str(local_base), local_files_only=True, torch_dtype=dtype
            )

        try:
            return model_cls.from_pretrained(
                base_model,
                cache_dir=str(cache_dir),
                local_files_only=True,
                torch_dtype=dtype,
            )
        except Exception:
            try:
                return model_cls.from_pretrained(
                    base_model, local_files_only=True, torch_dtype=dtype
                )
            except Exception:
                return model_cls.from_pretrained(
                    base_model, cache_dir=str(cache_dir), torch_dtype=dtype
                )

    def warmup(self) -> None:
        if self._warmed_up:
            return
        self._generate_messages_batch(
            [[{"role": "user", "content": "Trả lời ngắn: sẵn sàng."}]],
            max_new_tokens=1,
            max_input_tokens=64,
        )
        self._warmed_up = True

    def generate(self, question: str, contexts: list[RetrievedChunk]) -> str:
        answer, _included = self.generate_batch(
            [(question, contexts)],
            max_new_tokens=self.max_new_tokens,
            max_input_tokens=None,
        )[0]
        return answer

    def generate_batch(
        self,
        items: Sequence[tuple[str, list[RetrievedChunk]]],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[tuple[str, list[RetrievedChunk]]]:
        prepared = [
            self._build_rag_messages(question, contexts, max_input_tokens)
            for question, contexts in items
        ]
        messages = [item[0] for item in prepared]
        included_contexts = [item[1] for item in prepared]
        answers = self._generate_messages_batch(
            messages,
            max_new_tokens=max_new_tokens,
            max_input_tokens=max_input_tokens,
        )
        return list(zip(answers, included_contexts))

    def generate_without_context(
        self, question: str, allowed_sources: list[str] | None = None, strict: bool = True
    ) -> str:
        return self.generate_without_context_batch(
            [question],
            allowed_sources=[allowed_sources or []],
            strict=strict,
            max_new_tokens=self.max_new_tokens,
            max_input_tokens=None,
        )[0]

    def generate_without_context_batch(
        self,
        questions: Sequence[str],
        *,
        allowed_sources: Sequence[list[str]] | None = None,
        strict: bool = True,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        scopes = list(allowed_sources or [[] for _ in questions])
        if len(scopes) != len(questions):
            raise ValueError("allowed_sources must have the same length as questions.")
        messages = [self._build_finetuned_messages(question, sources) for question, sources in zip(questions, scopes)]
        answers = self._generate_messages_batch(
            messages,
            max_new_tokens=max_new_tokens,
            max_input_tokens=max_input_tokens,
        )
        retry_indexes = [
            index
            for index, (question, answer) in enumerate(zip(questions, answers))
            if self._looks_vietnamese(question) and self._contains_cjk(answer)
        ]
        if retry_indexes:
            retry_messages = [
                self._build_finetuned_messages(
                    questions[index],
                    scopes[index],
                    retry_language=True,
                )
                for index in retry_indexes
            ]
            retry_answers = self._generate_messages_batch(
                retry_messages,
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            )
            for index, retry_answer in zip(retry_indexes, retry_answers):
                answers[index] = (
                    FINETUNED_REFUSAL_MESSAGE
                    if self._contains_cjk(retry_answer)
                    else retry_answer
                )
        return [answer.strip() or FINETUNED_REFUSAL_MESSAGE for answer in answers]

    def _build_finetuned_messages(
        self,
        question: str,
        sources: list[str],
        retry_language: bool = False,
    ) -> list[dict[str, str]]:
        user_content = question
        if retry_language:
            user_content = (
                "Hãy trả lời lại hoàn toàn bằng tiếng Việt, không dùng chữ Hán, tiếng Trung hoặc tiếng Anh. "
                f"Câu hỏi: {question}"
            )
        return [
            {"role": "system", "content": build_finetuning_system_prompt(sources)},
            {"role": "user", "content": user_content},
        ]

    @staticmethod
    def _contains_cjk(text: str) -> bool:
        return bool(re.search(r"[\u3400-\u4dbf\u4e00-\u9fff]", text or ""))

    @staticmethod
    def _looks_vietnamese(text: str) -> bool:
        normalized = (text or "").casefold()
        if re.search(r"[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]", normalized):
            return True
        tokens = set(re.findall(r"[a-z]+", normalized))
        vietnamese_markers = {"la", "gi", "triet", "hoc", "tai", "lieu", "nhu", "the", "nao", "vi", "sao", "hay"}
        return len(tokens & vietnamese_markers) >= 2

    def _build_rag_messages(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        max_input_tokens: int | None,
    ) -> tuple[list[dict[str, str]], list[RetrievedChunk]]:
        system = (
            "Bạn là trợ lý học tập. Chỉ trả lời bằng tiếng Việt từ context; "
            "không thêm kiến thức, số liệu hay trích dẫn ngoài context. "
            "Nếu context có bằng chứng liên quan, hãy trả lời từ bằng chứng đó. "
            f"Chỉ nói \"{OUT_OF_SCOPE_MESSAGE}\" khi context hoàn toàn không liên quan. "
            "Nêu nguồn [Tên tài liệu, trang]."
        )
        included: list[RetrievedChunk] = []

        def messages_for(selected: list[RetrievedChunk]) -> list[dict[str, str]]:
            context_text = "\n\n".join(
                f"[{item.filename}, trang {item.page or '?'}]\n{item.content}"
                for item in selected
            )
            return [
                {"role": "system", "content": system},
                {
                    "role": "user",
                    "content": f"Câu hỏi: {question}\n\nContext:\n{context_text}",
                },
            ]

        candidates = contexts[:5]
        if max_input_tokens is None:
            return messages_for(candidates), candidates

        for context in candidates:
            trial = included + [context]
            if self._message_token_count(messages_for(trial)) <= max_input_tokens:
                included = trial
            else:
                break
        return messages_for(included), included

    def _message_token_count(self, messages: list[dict[str, str]]) -> int:
        tokens = self.tokenizer.apply_chat_template(
            messages, add_generation_prompt=True, tokenize=True
        )
        return len(tokens)

    def _generate_messages_batch(
        self,
        messages_batch: Sequence[list[dict[str, str]]],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        if not messages_batch:
            return []
        prompts = [
            self.tokenizer.apply_chat_template(
                messages, add_generation_prompt=True, tokenize=False
            )
            for messages in messages_batch
        ]
        with self._inference_lock:
            answers = self._generate_prompts_adaptive(
                prompts,
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            )
            self._warmed_up = True
            return answers

    def _generate_prompts_adaptive(
        self,
        prompts: Sequence[str],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        try:
            return self._generate_prompt_batch(
                prompts,
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            )
        except Exception as exc:
            if not self._is_cuda_oom(exc) or len(prompts) == 1:
                raise
            try:
                import torch

                if torch.cuda.is_available():
                    torch.cuda.empty_cache()
            except ImportError:
                pass
            midpoint = max(1, len(prompts) // 2)
            return self._generate_prompts_adaptive(
                prompts[:midpoint],
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            ) + self._generate_prompts_adaptive(
                prompts[midpoint:],
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            )

    def _generate_prompt_batch(
        self,
        prompts: Sequence[str],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        import torch

        tokenize_options = {
            "padding": True,
            "return_tensors": "pt",
        }
        if max_input_tokens is not None:
            tokenize_options.update({"truncation": True, "max_length": max_input_tokens})
        inputs = self.tokenizer(list(prompts), **tokenize_options)
        inputs = {key: value.to(self.device) for key, value in inputs.items()}
        input_length = inputs["input_ids"].shape[-1]
        with torch.inference_mode():
            output = self.model.generate(
                **inputs,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                repetition_penalty=1.15,
                no_repeat_ngram_size=3,
                use_cache=True,
                pad_token_id=self.tokenizer.pad_token_id,
                eos_token_id=self.tokenizer.eos_token_id,
            )
        return self.tokenizer.batch_decode(
            output[:, input_length:], skip_special_tokens=True
        )

    @staticmethod
    def _is_cuda_oom(exc: Exception) -> bool:
        return "out of memory" in str(exc).lower() and "cuda" in str(exc).lower()


class LocalBaseGenerator(LocalLoraGenerator):
    """Deterministic base-model generator used by the BASE_RAG benchmark."""

    def __init__(
        self,
        base_model: str,
        cache_dir: Path,
        max_new_tokens: int = 180,
    ) -> None:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        self.base_model = base_model
        self.adapter_dir = None
        self.max_new_tokens = max_new_tokens
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self._inference_lock = Lock()
        self._warmed_up = False

        local_tokenizer = find_cached_snapshot(cache_dir, base_model, "tokenizer.json")
        tokenizer_source = str(local_tokenizer) if local_tokenizer else base_model
        self.tokenizer = AutoTokenizer.from_pretrained(
            tokenizer_source,
            cache_dir=str(cache_dir),
            local_files_only=local_tokenizer is not None,
        )
        ensure_chat_template(self.tokenizer)
        if self.tokenizer.pad_token_id is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
        self.tokenizer.padding_side = "left"

        self.model = self._load_base_model(
            AutoModelForCausalLM, torch, base_model, cache_dir
        )
        self.model.to(self.device)
        self.model.eval()

    @property
    def adapter_version(self) -> None:
        return None
