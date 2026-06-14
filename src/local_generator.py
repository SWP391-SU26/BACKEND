from __future__ import annotations

from pathlib import Path

from .embeddings import find_cached_snapshot
from .rag_pipeline import OUT_OF_SCOPE_MESSAGE
from .storage import RetrievedChunk


class LocalLoraGenerator:
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
        load_kwargs = {
            "cache_dir": str(cache_dir),
            "local_files_only": True,
        }
        self.tokenizer = AutoTokenizer.from_pretrained(adapter_dir, local_files_only=True)
        local_base = find_cached_snapshot(cache_dir, base_model, "config.json")
        base = AutoModelForCausalLM.from_pretrained(str(local_base or base_model), **load_kwargs)
        self.model = PeftModel.from_pretrained(base, adapter_dir)
        self.model.to(self.device)
        self.model.eval()

    def generate(self, question: str, contexts: list[RetrievedChunk]) -> str:
        import torch

        context_text = "\n\n".join(
            f"[{item.filename}, trang {item.page or '?'}]\n{item.content}"
            for item in contexts[:5]
        )
        messages = [
            {
                "role": "system",
                "content": (
                    "Bạn là trợ lý học tập. Chỉ trả lời dựa trên tài liệu context. "
                    f"Nếu context không chứa câu trả lời, hãy nói: {OUT_OF_SCOPE_MESSAGE} "
                    "Trả lời ngắn gọn bằng tiếng Việt và nêu nguồn."
                ),
            },
            {
                "role": "user",
                "content": f"Context:\n{context_text}\n\nCâu hỏi: {question}",
            },
        ]
        inputs = self.tokenizer.apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
        )
        inputs = {key: value.to(self.device) for key, value in inputs.items()}
        with torch.no_grad():
            output = self.model.generate(
                **inputs,
                max_new_tokens=self.max_new_tokens,
                do_sample=False,
                repetition_penalty=1.05,
                pad_token_id=self.tokenizer.eos_token_id,
            )
        input_length = inputs["input_ids"].shape[-1]
        return self.tokenizer.decode(output[0][input_length:], skip_special_tokens=True).strip()

    def generate_without_context(self, question: str) -> str:
        return self._generate_messages(
            [
                {
                    "role": "system",
                    "content": "Bạn là trợ lý học tập. Trả lời bằng tiếng Việt rõ ràng và chính xác.",
                },
                {"role": "user", "content": question},
            ]
        )

    def _generate_messages(self, messages: list[dict[str, str]]) -> str:
        import torch

        inputs = self.tokenizer.apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
        )
        inputs = {key: value.to(self.device) for key, value in inputs.items()}
        with torch.no_grad():
            output = self.model.generate(
                **inputs,
                max_new_tokens=self.max_new_tokens,
                do_sample=False,
                repetition_penalty=1.05,
                pad_token_id=self.tokenizer.eos_token_id,
            )
        input_length = inputs["input_ids"].shape[-1]
        return self.tokenizer.decode(output[0][input_length:], skip_special_tokens=True).strip()
