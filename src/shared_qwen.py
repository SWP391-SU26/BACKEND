from __future__ import annotations

from contextlib import nullcontext
import json
from pathlib import Path
import re
from threading import RLock
from typing import Sequence

from .embeddings import find_cached_snapshot
from .finetuning import FINETUNED_REFUSAL_MESSAGE, build_finetuning_system_prompt
from .local_generator import ensure_chat_template
from .rag_pipeline import OUT_OF_SCOPE_MESSAGE
from .storage import RetrievedChunk


ANSWER_PROFILE_RULES = {
    "short": (
        "Trả lời trực tiếp trong 2-3 câu hoàn chỉnh. Không thêm tiêu đề hoặc danh sách "
        "nếu một đoạn ngắn đã đủ rõ."
    ),
    "factual": (
        "Mở đầu bằng câu trả lời trực tiếp. Nếu có từ hai chi tiết hỗ trợ trở lên, "
        "trình bày chúng bằng 2-4 gạch đầu dòng Markdown. Giữ câu trả lời trong khoảng 70-120 từ."
    ),
    "definition": (
        "Mở đầu bằng '**Định nghĩa:**' và một câu định nghĩa trực tiếp. Nếu chứng cứ đủ, "
        "thêm '**Đặc điểm chính:**' với 2-4 gạch đầu dòng ngắn. Giữ trong khoảng 80-160 từ."
    ),
    "list": (
        "Dùng danh sách Markdown gồm 3-7 ý chính; mỗi ý bắt đầu bằng từ khóa in đậm "
        "và có một lời giải thích ngắn. Không lặp lại cùng một ý dưới nhiều cách diễn đạt."
    ),
    "procedure": (
        "Dùng danh sách Markdown có đánh số theo đúng thứ tự trong tài liệu. "
        "Mỗi bước nêu hành động trước, sau đó mới ghi điều kiện hoặc lưu ý cần thiết."
    ),
    "comparison": (
        "Nếu tài liệu có đủ tiêu chí cho cả hai đối tượng, dùng bảng Markdown ngắn gồm "
        "các cột Tiêu chí và từng đối tượng, rồi thêm một câu kết luận. Nếu chưa đủ dữ liệu "
        "để lập bảng, dùng các gạch đầu dòng và nói rõ phần tài liệu chưa cung cấp."
    ),
    "summary": (
        "Tóm tắt thành 5-8 gạch đầu dòng Markdown đại diện cho các chủ đề chính. "
        "Mỗi ý chỉ 1-2 câu và không kể lại nguyên văn một đoạn tài liệu."
    ),
    "reasoning": (
        "Mở đầu bằng '**Trả lời trực tiếp:**' và một câu nêu luận điểm. Sau đó dùng "
        "'**Các lý do chính:**' với 2-4 gạch đầu dòng giải thích các cơ chế hoặc nguyên nhân "
        "khác nhau. Kết thúc bằng '**Kết luận:**' và một câu ngắn. Giữ trong khoảng 100-160 từ."
    ),
}


class SharedQwenRuntime:
    """One 4-bit Qwen instance shared by base RAG and LoRA inference."""

    def __init__(
        self,
        base_model: str,
        adapter_dir: Path,
        cache_dir: Path,
        max_input_tokens: int = 2048,
        max_new_tokens: int = 256,
    ) -> None:
        import torch
        from transformers import (
            AutoModelForCausalLM,
            AutoTokenizer,
            BitsAndBytesConfig,
        )

        self.base_model = base_model
        self.adapter_dir = adapter_dir
        self.cache_dir = cache_dir
        self.max_input_tokens = max_input_tokens
        self.max_new_tokens = max_new_tokens
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.quantization = "bnb-4bit-nf4" if self.device == "cuda" else "none"
        self._lock = RLock()
        self._base_warmed = False
        self._adapter_warmed = False
        self._adapter_loaded = False
        self._adapter_error: str | None = None
        self._manifest = self._read_manifest()

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

        local_model = find_cached_snapshot(cache_dir, base_model, "config.json")
        model_source = str(local_model) if local_model else base_model
        model_kwargs: dict = {
            "cache_dir": str(cache_dir),
            "local_files_only": local_model is not None,
            "low_cpu_mem_usage": True,
        }
        if self.device == "cuda":
            compute_dtype = (
                torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
            )
            model_kwargs.update(
                {
                    "device_map": {"": 0},
                    "quantization_config": BitsAndBytesConfig(
                        load_in_4bit=True,
                        bnb_4bit_quant_type="nf4",
                        bnb_4bit_compute_dtype=compute_dtype,
                        bnb_4bit_use_double_quant=True,
                    ),
                    "dtype": compute_dtype,
                }
            )
        else:
            model_kwargs["dtype"] = torch.bfloat16

        self.model = AutoModelForCausalLM.from_pretrained(model_source, **model_kwargs)
        self.model.eval()

    @property
    def warmed_up(self) -> bool:
        return self._base_warmed

    @property
    def adapter_version(self) -> str:
        return str(self._manifest.get("adapter_version") or self.adapter_dir.name)

    @property
    def adapter_loaded(self) -> bool:
        return self._adapter_loaded

    @property
    def adapter_verified(self) -> bool:
        return bool((self._manifest.get("quality_gate") or {}).get("passed"))

    @property
    def adapter_error(self) -> str | None:
        return self._adapter_error

    def _read_manifest(self) -> dict:
        path = self.adapter_dir / "training_manifest.json"
        if not path.exists():
            return {}
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {}

    def _validate_adapter(self, allow_unverified: bool) -> None:
        required = (
            "adapter_config.json",
            "adapter_model.safetensors",
            "training_manifest.json",
        )
        missing = [name for name in required if not (self.adapter_dir / name).exists()]
        if missing:
            raise RuntimeError(f"LoRA adapter is incomplete: missing {', '.join(missing)}.")
        manifest_model = str(self._manifest.get("base_model") or "").strip()
        if manifest_model != self.base_model:
            raise RuntimeError(
                "Base model does not match the adapter: "
                f"expected {self.base_model}, manifest has {manifest_model or 'none'}."
            )
        if not self.adapter_verified and not allow_unverified:
            raise RuntimeError(
                "LoRA adapter did not pass the quality gate. "
                "Only an explicitly acknowledged research benchmark may load it."
            )

    def ensure_adapter_loaded(self, allow_unverified: bool = False) -> None:
        with self._lock:
            if self._adapter_loaded:
                if not self.adapter_verified and not allow_unverified:
                    raise RuntimeError("The loaded LoRA adapter is UNVERIFIED.")
                return
            self._validate_adapter(allow_unverified)
            try:
                from peft import PeftModel

                self.model = PeftModel.from_pretrained(
                    self.model,
                    self.adapter_dir,
                    is_trainable=False,
                )
                self.model.eval()
                self._adapter_loaded = True
                self._adapter_error = None
            except Exception as exc:
                self._adapter_error = str(exc)
                raise

    def warmup(self) -> None:
        if self._base_warmed:
            return
        self._run_messages(
            [{"role": "user", "content": "Trả lời ngắn: sẵn sàng."}],
            use_adapter=False,
            max_input_tokens=64,
            max_new_tokens=1,
        )
        self._base_warmed = True

    def generate(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        *,
        history: Sequence[dict[str, str]] | None = None,
        standalone_query: str | None = None,
        answer_profile: str = "default",
        strict_prompt: bool = False,
        max_input_tokens: int | None = None,
        max_new_tokens: int | None = None,
        max_time_seconds: float | None = None,
    ) -> str:
        messages, _included = self._build_rag_messages(
            question,
            contexts,
            history=history or [],
            standalone_query=standalone_query,
            answer_profile=answer_profile,
            strict_prompt=strict_prompt,
            max_input_tokens=max_input_tokens or self.max_input_tokens,
        )
        return self._run_messages(
            messages,
            use_adapter=False,
            max_input_tokens=max_input_tokens or self.max_input_tokens,
            max_new_tokens=max_new_tokens or self.max_new_tokens,
            max_time_seconds=max_time_seconds,
        )

    def repair_unsupported_sentences(
        self,
        question: str,
        unsupported_sentences: Sequence[str],
        contexts: list[RetrievedChunk],
        *,
        max_input_tokens: int | None = None,
        max_new_tokens: int | None = None,
        max_time_seconds: float | None = None,
    ) -> str:
        if not unsupported_sentences or not contexts:
            return ""
        evidence = "\n\n".join(
            f"[{index}] {item.filename}, page {item.page or '?'}\n{item.content}"
            for index, item in enumerate(contexts, start=1)
        )
        prompt = (
            "Hãy viết các câu thay thế cho phần trả lời chưa được chứng cứ hỗ trợ bằng tiếng Việt tự nhiên, "
            "ngắn gọn trong 2 đến 4 câu. Mỗi khẳng định phải xuất phát trực tiếp từ CHỨNG CỨ TÀI LIỆU. "
            "Dùng đúng thuật ngữ xuất hiện trong chứng cứ, không đổi sang từ đồng nghĩa hoặc chú thích trong ngoặc. "
            "Giữ nguyên số liệu và tên riêng nếu chúng có trong chứng cứ; tuyệt đối không thêm tên người, "
            "thuật ngữ tiếng Anh hoặc kiến thức bên ngoài. Không mở đầu bằng lời dẫn, "
            "không trích dẫn dài, không tạo mục nguồn. Bỏ hẳn ý nào không thể sửa bằng chứng cứ. "
            "Chỉ trả về các câu đã sửa; trả chuỗi rỗng nếu không có câu nào hợp lệ.\n\n"
            f"Câu hỏi: {question}\n\n"
            f"CHỨNG CỨ TÀI LIỆU:\n{evidence}"
        )
        return self._run_messages(
            [{"role": "user", "content": prompt}],
            use_adapter=False,
            max_input_tokens=max_input_tokens or self.max_input_tokens,
            max_new_tokens=max_new_tokens or min(self.max_new_tokens, 160),
            max_time_seconds=max_time_seconds,
        ).strip()

    def complete_grounded_answer(
        self,
        question: str,
        current_answer: str,
        contexts: list[RetrievedChunk],
        *,
        answer_profile: str,
        max_input_tokens: int | None = None,
        max_new_tokens: int | None = None,
        max_time_seconds: float | None = None,
    ) -> str:
        if not contexts:
            return ""
        evidence = "\n\n".join(
            f"[{index}] {item.filename}, trang {item.page or '?'}\n{item.content}"
            for index, item in enumerate(contexts, start=1)
        )
        prompt = (
            "Viết lại câu trả lời hoàn chỉnh bằng tiếng Việt tự nhiên cho sinh viên. "
            "Chỉ sử dụng CHỨNG CỨ TÀI LIỆU, nhưng phải tổng hợp và diễn giải thay vì chép nguyên đoạn. "
            "Giữ lại các ý đúng trong CÂU TRẢ LỜI HIỆN TẠI, bổ sung các ý còn thiếu khi chứng cứ hỗ trợ. "
            "Giữ nguyên chính xác các thuật ngữ học thuật trọng tâm trong câu hỏi và chứng cứ; "
            "không thay bằng từ gần nghĩa hoặc từ thuộc lĩnh vực khác. "
            "Câu đầu tiên phải trả lời trực tiếp bằng chính các thuật ngữ đó. "
            "Không dịch thuật ngữ sang tiếng Anh, không chú thích trong ngoặc và không liệt kê chuỗi từ đồng nghĩa. "
            "Không thêm tên người, mốc lịch sử hoặc tranh luận trường phái nếu câu hỏi không yêu cầu các chi tiết đó. "
            "Mỗi khẳng định phải kiểm chứng được; không thêm kiến thức ngoài tài liệu, UUID hay mục nguồn. "
            "Dùng Markdown có chủ đích để sinh viên quét nhanh nội dung; không dồn nhiều ý độc lập vào một đoạn văn. "
            "Không tạo mục 'Nguồn' hoặc tự viết ký hiệu citation vì giao diện xử lý nguồn riêng. "
            f"{ANSWER_PROFILE_RULES.get(answer_profile, ANSWER_PROFILE_RULES['factual'])}\n\n"
            f"Câu hỏi: {question}\n\n"
            f"CÂU TRẢ LỜI HIỆN TẠI:\n{current_answer or '(chưa đủ ý)'}\n\n"
            f"CHỨNG CỨ TÀI LIỆU:\n{evidence}"
        )
        return self._run_messages(
            [{"role": "user", "content": prompt}],
            use_adapter=False,
            max_input_tokens=max_input_tokens or self.max_input_tokens,
            max_new_tokens=max_new_tokens or min(self.max_new_tokens, 240),
            max_time_seconds=max_time_seconds,
        ).strip()

    def generate_batch(
        self,
        items: Sequence[tuple],
        *,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[tuple[str, list[RetrievedChunk]]]:
        results: list[tuple[str, list[RetrievedChunk]]] = []
        for item in items:
            question, contexts = item[0], item[1]
            standalone_query = item[2] if len(item) > 2 else question
            answer_profile = item[3] if len(item) > 3 else "default"
            messages, included = self._build_rag_messages(
                question,
                contexts,
                history=[],
                standalone_query=standalone_query,
                answer_profile=answer_profile,
                strict_prompt=True,
                max_input_tokens=max_input_tokens or self.max_input_tokens,
            )
            answer = self._run_messages(
                messages,
                use_adapter=False,
                max_input_tokens=max_input_tokens or self.max_input_tokens,
                max_new_tokens=max_new_tokens,
            )
            results.append((answer, included))
        return results

    def generate_without_context(
        self,
        question: str,
        allowed_sources: list[str],
        *,
        allow_unverified: bool = False,
        max_input_tokens: int | None = None,
        max_new_tokens: int | None = None,
    ) -> str:
        self.ensure_adapter_loaded(allow_unverified)
        return self._run_messages(
            self._build_finetuned_messages(question, allowed_sources),
            use_adapter=True,
            allow_unverified=allow_unverified,
            max_input_tokens=max_input_tokens or self.max_input_tokens,
            max_new_tokens=max_new_tokens or self.max_new_tokens,
        )

    def generate_without_context_batch(
        self,
        questions: Sequence[str],
        *,
        allowed_sources: Sequence[list[str]] | None = None,
        strict: bool = True,
        allow_unverified: bool = False,
        max_new_tokens: int,
        max_input_tokens: int | None,
    ) -> list[str]:
        del strict
        scopes = list(allowed_sources or [[] for _ in questions])
        if len(scopes) != len(questions):
            raise ValueError("allowed_sources must have the same length as questions.")
        return [
            self.generate_without_context(
                question,
                scope,
                allow_unverified=allow_unverified,
                max_input_tokens=max_input_tokens,
                max_new_tokens=max_new_tokens,
            )
            for question, scope in zip(questions, scopes)
        ]

    def rewrite_query(
        self,
        question: str,
        *,
        history: Sequence[dict[str, str]] | None = None,
        intent: str = "factual",
        attempt: int = 1,
        evidence_hints: Sequence[str] | None = None,
    ) -> str:
        history_text = "\n".join(
            f"{item.get('role', 'user')}: {str(item.get('content') or '')[:400]}"
            for item in list(history or [])[-12:]
            if item.get("content")
        )
        hints = "\n".join(f"- {hint[:500]}" for hint in list(evidence_hints or [])[:2])
        prompt = (
            "Viết lại câu hỏi mới nhất thành một truy vấn tìm kiếm học thuật độc lập bằng tiếng Việt. "
            "Giữ nguyên tên riêng, số liệu, khái niệm, yêu cầu của câu hỏi và dấu tiếng Việt. "
            "Chỉ dùng lịch sử để làm rõ các đại từ như 'điều đó', 'nội dung trên'. "
            "Truy vấn phải ngắn gọn trong một câu. Không trả lời câu hỏi, không thêm nhãn hoặc tiền tố. "
            "Chỉ xuất truy vấn đã viết lại.\n\n"
            f"Ý định: {intent}\nLần thử: {attempt}\n"
            f"Lịch sử:\n{history_text or '(không có)'}\n"
            f"Gợi ý bằng chứng:\n{hints or '(không có)'}\n"
            f"Câu hỏi mới nhất: {question}"
        )
        rewritten = self._run_messages(
            [{"role": "user", "content": prompt}],
            use_adapter=False,
            max_input_tokens=768,
            max_new_tokens=96,
        )
        cleaned = re.sub(
            r"^(rewritten query|truy vấn viết lại|truy van viet lai|truy vấn|truy van)\s*:\s*",
            "",
            rewritten,
            flags=re.I,
        )
        return cleaned.strip().strip("\"'") or question.strip()

    def _build_finetuned_messages(
        self,
        question: str,
        sources: list[str],
    ) -> list[dict[str, str]]:
        return [
            {"role": "system", "content": build_finetuning_system_prompt(sources)},
            {"role": "user", "content": question},
        ]

    def _build_rag_messages(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        *,
        history: Sequence[dict[str, str]],
        standalone_query: str | None,
        answer_profile: str,
        strict_prompt: bool,
        max_input_tokens: int,
    ) -> tuple[list[dict[str, str]], list[RetrievedChunk]]:
        system = (
            "Bạn là trợ lý học tập cho sinh viên. Chỉ được dùng thông tin trong DOCUMENT CONTEXT "
            "để đưa ra các khẳng định về môn học. Lịch sử chỉ dùng để hiểu đại từ và ý định hỏi tiếp, "
            "không phải là nguồn kiến thức. "
            f"Nếu context không đủ bằng chứng, chỉ trả về đúng câu: {OUT_OF_SCOPE_MESSAGE} "
            "Trả lời cùng ngôn ngữ với câu hỏi; câu hỏi tiếng Việt phải được trả lời tự nhiên, "
            "rõ ràng và đúng chính tả tiếng Việt. Không chép nguyên văn dài dòng, không bịa kiến thức, "
            "không in UUID và không tự tạo mục nguồn vì giao diện sẽ hiển thị citation riêng. "
            "Câu trả lời phải là một lời giải hoàn chỉnh, không bắt đầu hoặc kết thúc bằng mẩu câu bị cắt. "
            "Ưu tiên tổng hợp bằng lời văn mạch lạc; chỉ dùng thuật ngữ học thuật thực sự có trong context. "
            "Giữ nguyên chính xác thuật ngữ trọng tâm trong câu hỏi và context, không thay bằng từ gần nghĩa "
            "hoặc từ thuộc lĩnh vực khác. Câu đầu tiên phải trả lời trực tiếp bằng chính các thuật ngữ đó. "
            "Chỉ dùng bằng chứng trực tiếp trả lời câu hỏi; bỏ bối cảnh lịch sử, tên người, ví dụ và phần tranh luận "
            "không cần thiết nếu người dùng không hỏi. "
            "Dùng Markdown có chủ đích để sinh viên quét nhanh nội dung; không dồn nhiều ý độc lập vào một đoạn văn. "
            "Không tạo mục 'Nguồn', không tự viết [1], [2] hoặc tên file vì giao diện sẽ hiển thị citation riêng. "
            f"{ANSWER_PROFILE_RULES.get(answer_profile, ANSWER_PROFILE_RULES['short'])}"
        )
        if strict_prompt:
            system += (
                " Kiểm tra từng khẳng định trước khi trả lời và bỏ mọi chi tiết không được context hỗ trợ."
            )

        safe_history = [
            {
                "role": "assistant" if item.get("role") == "assistant" else "user",
                "content": str(item.get("content") or "")[:600],
            }
            for item in list(history)[-6:]
            if item.get("content")
        ]

        def messages_for(selected: list[RetrievedChunk]) -> list[dict[str, str]]:
            context_text = "\n\n".join(
                f"[SOURCE {index} | {item.filename} | page {item.page or '?'}]\n{item.content}"
                for index, item in enumerate(selected, start=1)
            )
            return [
                {"role": "system", "content": system},
                *safe_history,
                {
                    "role": "user",
                    "content": (
                        f"Truy vấn độc lập: {standalone_query or question}\n"
                        f"Câu hỏi: {question}\n\nDOCUMENT CONTEXT:\n{context_text}"
                    ),
                },
            ]

        included: list[RetrievedChunk] = []
        for context in contexts:
            trial = included + [context]
            if self._message_token_count(messages_for(trial)) <= max_input_tokens:
                included = trial
            else:
                break
        return messages_for(included), included

    def _message_token_count(self, messages: list[dict[str, str]]) -> int:
        return len(
            self.tokenizer.apply_chat_template(
                messages,
                add_generation_prompt=True,
                tokenize=True,
            )
        )

    def _adapter_context(self, use_adapter: bool):
        if use_adapter or not self._adapter_loaded:
            return nullcontext()
        return self.model.disable_adapter()

    def _run_messages(
        self,
        messages: list[dict[str, str]],
        *,
        use_adapter: bool,
        allow_unverified: bool = False,
        max_input_tokens: int,
        max_new_tokens: int,
        max_time_seconds: float | None = None,
    ) -> str:
        import torch

        with self._lock:
            if use_adapter:
                self.ensure_adapter_loaded(allow_unverified)
                if hasattr(self.model, "set_adapter"):
                    self.model.set_adapter("default")
            prompt = self.tokenizer.apply_chat_template(
                messages,
                add_generation_prompt=True,
                tokenize=False,
            )
            inputs = self.tokenizer(
                [prompt],
                padding=True,
                truncation=True,
                max_length=max_input_tokens,
                return_tensors="pt",
            )
            inputs = {key: value.to(self.device) for key, value in inputs.items()}
            input_length = inputs["input_ids"].shape[-1]
            with self._adapter_context(use_adapter), torch.inference_mode():
                generation_options = {
                    "max_new_tokens": max_new_tokens,
                    "do_sample": False,
                    "repetition_penalty": 1.02,
                    "use_cache": True,
                    "pad_token_id": self.tokenizer.pad_token_id,
                    "eos_token_id": self.tokenizer.eos_token_id,
                }
                if max_time_seconds is not None:
                    generation_options["max_time"] = max_time_seconds
                output = self.model.generate(
                    **inputs,
                    **generation_options,
                )
            answer = self.tokenizer.decode(
                output[0, input_length:],
                skip_special_tokens=True,
            ).strip()
            if use_adapter:
                self._adapter_warmed = True
            else:
                self._base_warmed = True
            return answer or FINETUNED_REFUSAL_MESSAGE if use_adapter else answer
