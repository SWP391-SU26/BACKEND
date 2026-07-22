from __future__ import annotations

from pathlib import Path
from threading import Lock
from typing import Sequence

from .embeddings import find_cached_snapshot
from .rag_pipeline import OUT_OF_SCOPE_MESSAGE
from .storage import RetrievedChunk


class LocalLoraGenerator:
    """Single-GPU LoRA inference with bounded prompts and adaptive batching."""

    def __init__(
        self,
        base_model: str,
        adapter_dir: Path,
        cache_dir: Path,
        max_new_tokens: int = 180,
    ) -> None:
        import torch
        from peft import PeftModel
        from transformers import AutoModelForCausalLM, AutoTokenizer

        if not adapter_dir.exists():
            raise FileNotFoundError(f"Không tìm thấy LoRA adapter: {adapter_dir}")

        self.base_model = base_model
        self.adapter_dir = adapter_dir
        self.max_new_tokens = max_new_tokens
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self._inference_lock = Lock()
        self._warmed_up = False

        self.tokenizer = AutoTokenizer.from_pretrained(adapter_dir, local_files_only=True)
        if self.tokenizer.pad_token_id is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
        self.tokenizer.padding_side = "left"

        base = self._load_base_model(AutoModelForCausalLM, torch, base_model, cache_dir)
        adapter = PeftModel.from_pretrained(base, adapter_dir)
        # Benchmark inference never switches adapters. Merging removes PEFT dispatch
        # overhead and keeps just one FP16 model in GPU memory.
        self.model = adapter.merge_and_unload()
        self.model.to(self.device)
        self.model.eval()

    @property
    def warmed_up(self) -> bool:
        return self._warmed_up

    def _load_base_model(self, model_cls, torch_module, base_model: str, cache_dir: Path):
        dtype = torch_module.float16 if self.device == "cuda" else torch_module.float32
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

    def generate(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        conversation_history: list[dict[str, str]] | None = None,
    ) -> str:
        messages, _included = self._build_rag_messages(
            question, contexts, None, conversation_history or []
        )
        return self._generate_messages_batch(
            [messages], max_new_tokens=self.max_new_tokens, max_input_tokens=None
        )[0]

    def generate_batch(
        self,
        items: Sequence[tuple[str, list[RetrievedChunk]]],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[tuple[str, list[RetrievedChunk]]]:
        prepared = [
            self._build_rag_messages(question, contexts, max_input_tokens, [])
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

    def generate_without_context(self, question: str) -> str:
        return self.generate_without_context_batch(
            [question], max_new_tokens=self.max_new_tokens, max_input_tokens=None
        )[0]

    def generate_without_context_batch(
        self,
        questions: Sequence[str],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        messages = [
            [
                {
                    "role": "system",
                    "content": "Bạn là trợ lý học tập. Trả lời ngắn gọn, rõ ràng và chính xác bằng tiếng Việt.",
                },
                {"role": "user", "content": question},
            ]
            for question in questions
        ]
        return self._generate_messages_batch(
            messages,
            max_new_tokens=max_new_tokens,
            max_input_tokens=max_input_tokens,
        )

    def _build_rag_messages(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        max_input_tokens: int | None,
        conversation_history: list[dict[str, str]] | None = None,
    ) -> tuple[list[dict[str, str]], list[RetrievedChunk]]:
        system = (
            "Bạn là trợ lý học tập. Chỉ trả lời dựa trên tài liệu context. "
            f"Nếu context không chứa câu trả lời, hãy nói: {OUT_OF_SCOPE_MESSAGE} "
            "Trả lời ngắn gọn bằng tiếng Việt và nêu nguồn."
        )
        included: list[RetrievedChunk] = []

        def messages_for(selected: list[RetrievedChunk]) -> list[dict[str, str]]:
            context_text = "\n\n".join(
                f"[{item.filename}, trang {item.page or '?'}]\n{item.content}"
                for item in selected
            )
            history = [
                {"role": item["role"], "content": item["content"]}
                for item in (conversation_history or [])[-6:]
                if item.get("role") in {"user", "assistant"} and item.get("content")
            ]
            return [
                {"role": "system", "content": system},
                *history,
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
            return self._generate_prompts_adaptive(
                prompts,
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            )

    def _generate_prompts_adaptive(
        self,
        prompts: Sequence[str],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        import torch

        try:
            return self._generate_prompt_batch(
                prompts,
                max_new_tokens=max_new_tokens,
                max_input_tokens=max_input_tokens,
            )
        except Exception as exc:
            if not self._is_cuda_oom(exc) or len(prompts) == 1:
                raise
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
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
                repetition_penalty=1.05,
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
