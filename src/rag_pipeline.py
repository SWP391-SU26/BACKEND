from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any
import importlib.util
import re
import unicodedata

from .chunker import chunk_pages
from .config import AppSettings
from .document_loader import load_document
from .embeddings import EmbeddingProvider
from .storage import RetrievedChunk, SQLiteStore
from .text_utils import file_sha256, split_sentences, tokenize


OUT_OF_SCOPE_MESSAGE = "Tôi chưa tìm thấy thông tin này trong tài liệu đã cung cấp."


@dataclass(frozen=True)
class IngestResult:
    document_id: str
    filename: str
    num_pages: int
    num_chunks: int


@dataclass(frozen=True)
class ChatResult:
    answer: str
    sources: list[dict[str, Any]]
    retrieved: list[RetrievedChunk]


class RAGPipeline:
    def __init__(
        self,
        settings: AppSettings,
        store: SQLiteStore,
        embedding_provider: EmbeddingProvider,
    ) -> None:
        self.settings = settings
        self.store = store
        self.embedding_provider = embedding_provider
        self.local_generator = None
        self.local_generator_error: str | None = None
        self.finetuned_scope_guard = None

    def ingest_file(self, path: Path, subject: str, chapter: str) -> IngestResult:
        pages = load_document(path)
        chunks = chunk_pages(
            pages,
            chunk_size=self.settings.chunk_size,
            overlap=self.settings.chunk_overlap,
        )
        if not chunks:
            raise RuntimeError("No chunks were created from this document.")

        embeddings = self.embedding_provider.embed_texts([chunk.text for chunk in chunks])
        file_hash = file_sha256(path)
        document_id = self.store.find_document_by_hash(file_hash)
        if document_id:
            self.store.delete_document_chunks(document_id, self.embedding_provider.model)
        else:
            document_id = self.store.add_document(
                filename=path.name,
                original_path=path,
                subject=subject,
                chapter=chapter,
                file_hash=file_hash,
            )
        self.store.add_chunks(
            document_id=document_id,
            chunks=chunks,
            embeddings=embeddings,
            embedding_model=self.embedding_provider.model,
        )
        return IngestResult(
            document_id=document_id,
            filename=path.name,
            num_pages=len(pages),
            num_chunks=len(chunks),
        )

    def answer(self, session_id: str, question: str, subject: str | None = None) -> ChatResult:
        question = question.strip()
        self.store.add_message(session_id, "user", question)

        query_embedding = self.embedding_provider.embed_query(question)
        retrieved = self.store.search_chunks(
            query_embedding=query_embedding,
            embedding_model=self.embedding_provider.model,
            top_k=self.settings.top_k,
            query_text=question,
            subject=subject,
            semantic_weight=self.settings.semantic_weight,
        )
        if not retrieved or retrieved[0].score < self.settings.min_retrieval_score:
            self.store.add_message(session_id, "assistant", OUT_OF_SCOPE_MESSAGE, [])
            return ChatResult(answer=OUT_OF_SCOPE_MESSAGE, sources=[], retrieved=retrieved)

        sources = build_sources(retrieved)
        answer = self._generate_answer(question, retrieved, sources)
        self.store.add_message(session_id, "assistant", answer, sources)
        return ChatResult(answer=answer, sources=sources, retrieved=retrieved)

    def _generate_answer(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        sources: list[dict[str, Any]],
        strict: bool = False,
    ) -> str:
        provider = self.settings.generation_provider.lower().strip()
        if provider in {"auto", "lora", "local"}:
            generator = self._get_local_generator()
            if generator:
                try:
                    answer = generator.generate(question, contexts)
                    if answer:
                        return answer
                except Exception as exc:
                    self.local_generator_error = str(exc)
                    if strict and provider in {"lora", "local"}:
                        raise RuntimeError(f"Local LoRA generation failed: {exc}") from exc
        if provider in {"auto", "openai"} and self.settings.openai_api_key:
            try:
                return self._generate_with_openai(question, contexts)
            except Exception as exc:
                if strict and provider == "openai":
                    raise RuntimeError(f"OpenAI generation failed: {exc}") from exc
                return self._generate_extractive_answer(question, contexts, sources)
        if strict and provider in {"lora", "local", "openai"}:
            raise RuntimeError(self.local_generator_error or "No strict generation model is ready.")
        return self._generate_extractive_answer(question, contexts, sources)

    def _get_local_generator(self):
        if self.local_generator is not None:
            return self.local_generator
        if self.local_generator_error is not None:
            return None
        try:
            from .local_generator import LocalLoraGenerator

            self.local_generator = LocalLoraGenerator(
                base_model=self.settings.local_base_model,
                adapter_dir=self.settings.lora_adapter_dir,
                cache_dir=self.settings.model_cache_dir,
                max_new_tokens=self.settings.local_max_new_tokens,
                enforce_quality_gate=not self.settings.allow_unverified_finetuned,
            )
            return self.local_generator
        except Exception as exc:
            self.local_generator_error = str(exc)
            return None

    def generation_status(self) -> dict[str, Any]:
        provider = self.settings.generation_provider.lower().strip()
        required_modules = {name: importlib.util.find_spec(name) is not None for name in ("torch", "transformers", "peft")}
        training_modules = {
            name: importlib.util.find_spec(name) is not None
            for name in ("torch", "transformers", "datasets", "peft", "trl", "accelerate")
        }
        adapter_files = ["adapter_config.json", "adapter_model.safetensors", "tokenizer_config.json"]
        adapter_ready = self.settings.lora_adapter_dir.is_dir() and all(
            (self.settings.lora_adapter_dir / name).exists() for name in adapter_files
        )
        manifest_path = self.settings.lora_adapter_dir / "training_manifest.json"
        manifest = None
        if manifest_path.exists():
            try:
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                manifest = None
        quality_gate = (manifest or {}).get("quality_gate") or {
            "passed": False,
            "checks": {"verified_manifest": False},
        }
        manifest_base_model = str((manifest or {}).get("base_model") or "").strip()
        base_model_matches = bool(manifest_base_model) and (
            manifest_base_model == self.settings.local_base_model
        )
        quality_gate_passed = bool(quality_gate.get("passed"))
        quality_gate_overridden = self.settings.allow_unverified_finetuned and not quality_gate_passed
        configured_ready = (
            all(required_modules.values())
            and adapter_ready
            and bool(self.settings.local_base_model)
            and base_model_matches
            and (quality_gate_passed or self.settings.allow_unverified_finetuned)
        )
        inference_ready = configured_ready and self.local_generator is not None \
            and self.local_generator.warmed_up and self.local_generator_error is None
        generation_ready = provider == "extractive" or inference_ready or bool(self.settings.openai_api_key)
        return {
            "configured_provider": provider,
            "adapter_dir": str(self.settings.lora_adapter_dir),
            "adapter_exists": self.settings.lora_adapter_dir.exists(),
            "adapter_ready": adapter_ready,
            "quality_gate": quality_gate,
            "quality_gate_overridden": quality_gate_overridden,
            "evaluation_metrics": (manifest or {}).get("evaluation_metrics", {}),
            "trained_sources": (manifest or {}).get("sources", []),
            "configured_ready": configured_ready,
            "base_model": self.settings.local_base_model,
            "manifest_base_model": manifest_base_model or None,
            "base_model_matches": base_model_matches,
            "dependencies": required_modules,
            "inference_ready": inference_ready,
            "training_ready": all(training_modules.values()),
            "training_dependencies": training_modules,
            "generation_ready": generation_ready,
            "local_model_loaded": self.local_generator is not None,
            "local_model_warmed_up": bool(self.local_generator and self.local_generator.warmed_up),
            "local_model_error": self.local_generator_error,
            "openai_configured": bool(self.settings.openai_api_key),
        }

    def warmup_local_model(self) -> None:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model is not ready.")
        try:
            generator.warmup()
        except Exception as exc:
            self.local_generator_error = str(exc)
            raise

    def generate_rag_batch(
        self,
        items: list[tuple[str, list[RetrievedChunk]]],
    ) -> list[tuple[str, list[RetrievedChunk]]]:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model is not ready.")
        return generator.generate_batch(
            items,
            max_new_tokens=self.settings.benchmark_max_new_tokens,
            max_input_tokens=self.settings.benchmark_max_input_tokens,
        )

    def generate_without_retrieval_batch(
        self,
        questions: list[str],
        allowed_sources: list[list[str]] | None = None,
        strict: bool = True,
    ) -> list[str]:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model is not ready.")
        return generator.generate_without_context_batch(
            questions,
            allowed_sources=allowed_sources,
            strict=strict,
            max_new_tokens=self.settings.benchmark_max_new_tokens,
            max_input_tokens=self.settings.benchmark_max_input_tokens,
        )

    def generate_without_retrieval(
        self, question: str, allowed_sources: list[str] | None = None, strict: bool = True
    ) -> str:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model chưa sẵn sàng.")
        return generator.generate_without_context(question, allowed_sources or [], strict=strict)

    def assess_finetuned_scope(self, question: str, selected_sources: list[str]):
        if self.finetuned_scope_guard is None:
            from .finetuned_scope import FineTunedScopeGuard

            self.finetuned_scope_guard = FineTunedScopeGuard(
                [
                    self.settings.finetuning_dir / "train.jsonl",
                    self.settings.finetuning_dir / "validation.jsonl",
                ],
                self.embedding_provider,
                min_similarity=self.settings.finetuned_scope_min_similarity,
            )
        return self.finetuned_scope_guard.decide(question, selected_sources)

    def _generate_with_openai(self, question: str, contexts: list[RetrievedChunk]) -> str:
        from openai import OpenAI

        client = OpenAI(api_key=self.settings.openai_api_key)
        context_text = "\n\n".join(
            f"[{index}] {format_source(chunk)}\n{chunk.content}"
            for index, chunk in enumerate(contexts, start=1)
        )
        system_prompt = (
            "Bạn là trợ lý học tập cho sinh viên. "
            "Chỉ trả lời dựa trên các đoạn tài liệu được cung cấp trong context. "
            f"Nếu tài liệu không chứa thông tin cần thiết, hãy nói: \"{OUT_OF_SCOPE_MESSAGE}\" "
            "Luôn trích dẫn nguồn theo dạng [Tên tài liệu, trang/chương]. "
            "Không tự bịa kiến thức ngoài tài liệu. Trả lời bằng tiếng Việt rõ ràng, dễ hiểu."
        )
        response = client.chat.completions.create(
            model=self.settings.openai_chat_model,
            temperature=0.1,
            messages=[
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": f"Context:\n{context_text}\n\nCâu hỏi: {question}",
                },
            ],
        )
        return response.choices[0].message.content or OUT_OF_SCOPE_MESSAGE

    def _generate_extractive_answer(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        sources: list[dict[str, Any]],
    ) -> str:
        if self._is_list_question(question):
            return self._generate_list_answer(question, contexts)

        if self._is_summary_question(question):
            return self._generate_summary_answer(contexts)

        passages = [
            (passage, chunk)
            for chunk in contexts
            for passage in self._answer_passages(chunk.content)
        ][:180]
        if not passages:
            return OUT_OF_SCOPE_MESSAGE

        semantic_scores = self._semantic_answer_scores(question, [passage for passage, _chunk in passages])
        query_terms = set(tokenize(self._normalize_for_summary(question)))
        question_form = self._question_form(question)
        candidates: list[tuple[float, str, RetrievedChunk]] = []
        for index, (passage, chunk) in enumerate(passages):
            terms = set(tokenize(self._normalize_for_summary(passage)))
            lexical = len(query_terms & terms) / max(1, len(query_terms))
            semantic = semantic_scores[index]
            score = (semantic * 0.72) + (lexical * 0.18) + (max(0.0, chunk.score) * 0.10)
            form_boost = self._answer_form_boost(question_form, passage)
            if question_form != "definition" or lexical >= 0.30:
                score += form_boost
            if semantic >= 0.22 or lexical > 0:
                candidates.append((score, passage, chunk))

        candidates.sort(key=lambda item: item[0], reverse=True)
        selected_candidates = self._select_answer_candidates(question, question_form, candidates)
        if not selected_candidates:
            return OUT_OF_SCOPE_MESSAGE

        selected = [sentence for _score, sentence, _chunk in selected_candidates]
        selected_chunks: list[RetrievedChunk] = []
        seen_chunk_ids: set[str] = set()
        for _score, _sentence, chunk in selected_candidates:
            if chunk.chunk_id in seen_chunk_ids:
                continue
            seen_chunk_ids.add(chunk.chunk_id)
            selected_chunks.append(chunk)

        source_text = "; ".join(
            f"[{source['filename']}, {source['location']}]" for source in build_sources(selected_chunks)[:3]
        )
        body = " ".join(selected)
        return f"Dựa trên tài liệu, {body}\n\nNguồn: {source_text}"

    def _answer_passages(self, content: str) -> list[str]:
        passages: list[str] = []
        for sentence in split_sentences(content):
            pieces = re.split(r"(?<=[;])\s+|\s+(?=[+•])|(?=\s+[a-zA-Z]\))", sentence)
            for piece in pieces:
                words = piece.split()
                if not words or self._is_weak_answer_passage(piece):
                    continue
                if len(words) <= 90 and len(piece) <= 650:
                    passages.append(self._trim_summary_item(piece, limit=650))
                    continue
                start = 0
                while start < len(words):
                    window = words[start:start + 80]
                    passages.append(" ".join(window))
                    if start + 80 >= len(words):
                        break
                    start += 65
        return passages

    def _is_weak_answer_passage(self, passage: str) -> bool:
        cleaned = " ".join((passage or "").split())
        words = cleaned.split()
        if len(words) < 5 or cleaned.endswith(":") or cleaned.endswith("?"):
            return True
        normalized = self._normalize_for_summary(cleaned)
        return normalized in {"su doi lap giua phuong phap sieu hinh va phuong phap bien chung"}

    def _semantic_answer_scores(self, question: str, passages: list[str]) -> list[float]:
        try:
            vectors = self.embedding_provider.embed_texts([question, *passages])
            query_vector = vectors[0]
            return [
                sum(left * right for left, right in zip(query_vector, vector))
                for vector in vectors[1:]
            ]
        except Exception:
            return [0.0] * len(passages)

    def _question_form(self, question: str) -> str:
        normalized = self._normalize_for_summary(question)
        if self._is_summary_question(question):
            return "summary"
        if any(term in normalized for term in ("so sanh", "phan biet", "khac nhau", "compare", "versus")):
            return "comparison"
        if any(term in normalized for term in ("tai sao", "vi sao", "nguyen nhan", "why", "vai tro", "y nghia")):
            return "reasoning"
        if any(term in normalized for term in ("quy trinh", "cac buoc", "trinh tu", "how to")):
            return "procedure"
        if any(term in normalized for term in ("la gi", "dinh nghia", "khai niem", "duoc hieu", "meaning")):
            return "definition"
        if self._is_list_question(question):
            return "list"
        return "fact"

    def _answer_form_boost(self, question_form: str, passage: str) -> float:
        normalized = self._normalize_for_summary(passage)
        if question_form == "definition" and any(
            term in f" {normalized} " for term in (" la ", "duoc hieu", "khai niem", "khai quat lai")
        ):
            return 0.18
        if question_form == "reasoning" and any(
            term in normalized for term in (" vi ", " do ", "boi", "nguyen nhan", "cho nen", "nham")
        ):
            return 0.06
        if question_form == "procedure" and any(
            term in normalized for term in ("buoc", "truoc het", "tiep theo", "sau do", "quy trinh")
        ):
            return 0.06
        return 0.0

    def _select_answer_candidates(
        self,
        question: str,
        question_form: str,
        candidates: list[tuple[float, str, RetrievedChunk]],
    ) -> list[tuple[float, str, RetrievedChunk]]:
        selected: list[tuple[float, str, RetrievedChunk]] = []
        if question_form == "comparison":
            requires_method_phrase = "phuong phap" in self._normalize_for_summary(question)
            for keywords in self._comparison_keyword_groups(question):
                match = next(
                    (candidate for candidate in candidates if all(
                        keyword in self._normalize_for_summary(candidate[1]) for keyword in keywords
                    ) and (not requires_method_phrase
                           or "phuong phap" in self._normalize_for_summary(candidate[1]))
                     and candidate not in selected),
                    None,
                )
                if match is not None:
                    selected.append(match)
            if len(selected) >= 2:
                return selected

        if question_form == "definition":
            limit = 1
        elif question_form in {"comparison", "reasoning", "procedure"}:
            limit = 5
        else:
            limit = 3
        for candidate in candidates:
            normalized = self._normalize_for_summary(candidate[1])
            if any(self._normalize_for_summary(item[1]) == normalized for item in selected):
                continue
            selected.append(candidate)
            if len(selected) >= limit:
                break
        return selected

    def _comparison_keyword_groups(self, question: str) -> list[set[str]]:
        normalized = self._normalize_for_summary(question)
        normalized = re.sub(r"^(so sanh|phan biet)\s+", "", normalized)
        parts = re.split(r"\b(?:va|voi|versus|vs)\b", normalized, maxsplit=1)
        if len(parts) != 2:
            return []
        ignored = {"phuong", "phap", "giua", "hai", "su", "khac", "nhau"}
        groups = [set(tokenize(part)) - ignored for part in parts]
        shared = groups[0] & groups[1]
        return [group - shared for group in groups if group - shared]

    def _generate_list_answer(self, question: str, contexts: list[RetrievedChunk]) -> str:
        selected_chunks: list[RetrievedChunk] = []
        items: list[str] = []
        seen_items: set[str] = set()

        for chunk in sorted(contexts, key=lambda item: (item.page or 999999, item.chunk_id)):
            for item in self._items_from_chunk_for_list(chunk.content):
                key = self._normalize_for_summary(item)
                if not key or key in seen_items or self._is_weak_list_item(item):
                    continue
                seen_items.add(key)
                items.append(self._trim_summary_item(item, limit=180))
                if chunk not in selected_chunks:
                    selected_chunks.append(chunk)
                if len(items) >= 40:
                    break
            if len(items) >= 40:
                break

        if not items:
            return OUT_OF_SCOPE_MESSAGE

        source_text = "; ".join(
            f"[{source['filename']}, {source['location']}]" for source in build_sources(selected_chunks)[:12]
        )
        body = "\n".join(f"- {item}" for item in items)
        return f"Dựa trên tài liệu, {self._list_answer_label(question)}:\n{body}\n\nNguồn: {source_text}"

    def _items_from_chunk_for_list(self, content: str) -> list[str]:
        sentences = split_sentences(content)
        if sentences:
            return sentences
        cleaned = " ".join((content or "").split())
        return [cleaned] if cleaned else []

    def _is_weak_list_item(self, item: str) -> bool:
        normalized = self._normalize_for_summary(item)
        if "tu vung" in normalized and len(normalized.split()) <= 4:
            return True
        if any(
            (0x3040 <= ord(char) <= 0x30FF) or (0x4E00 <= ord(char) <= 0x9FFF)
            for char in item
        ):
            return False
        if len(normalized) < 2:
            return True
        return normalized in {"mon hoc", "giang vien"}

    def _list_answer_label(self, question: str) -> str:
        normalized = self._normalize_for_summary(question)
        if "tu vung" in normalized or any(term in normalized for term in ("語彙", "ことば")):
            return "danh sách từ vựng tìm thấy"
        if "ngu phap" in normalized or "mau cau" in normalized or any(
            term in normalized for term in ("文法", "ぶんぽう")
        ):
            return "các điểm ngữ pháp/mẫu câu tìm thấy"
        if "bai tap" in normalized or "vi du" in normalized:
            return "các ví dụ/bài tập tìm thấy"
        return "các nội dung tìm thấy"

    def _generate_summary_answer(self, contexts: list[RetrievedChunk]) -> str:
        selected_chunks: list[RetrievedChunk] = []
        selected_items: list[str] = []
        seen_items: set[str] = set()

        for chunk in sorted(contexts, key=lambda item: (item.page or 999999, item.chunk_id)):
            item = self._summary_item_from_chunk(chunk.content)
            if not item:
                continue
            key = self._normalize_for_summary(item)
            if key in seen_items or self._is_weak_summary_item(item):
                continue
            seen_items.add(key)
            selected_items.append(item)
            selected_chunks.append(chunk)
            if len(selected_items) >= 16:
                break

        if not selected_items:
            return OUT_OF_SCOPE_MESSAGE

        source_text = "; ".join(
            f"[{source['filename']}, {source['location']}]" for source in build_sources(selected_chunks)[:12]
        )
        body = "\n".join(f"- {item}" for item in selected_items)
        return f"Dựa trên tài liệu, nội dung chính gồm:\n{body}\n\nNguồn: {source_text}"

    def _summary_item_from_chunk(self, content: str) -> str:
        sentences = split_sentences(content)
        if not sentences:
            return ""

        preferred_keywords = [
            "tu vung",
            "ことば",
            "ngu phap",
            "ぶんぽう",
            "tro tu",
            "phuong tien",
            "cach thuc",
            "cac nhom dong tu",
            "dong tu dac biet",
            "the て",
            "ください",
            "かた",
            "わかります",
            "どの",
            "どれ",
            "チャレンジ",
        ]

        for sentence in sentences:
            normalized = self._normalize_for_summary(sentence)
            if any(keyword in normalized for keyword in preferred_keywords):
                return self._trim_summary_item(sentence)

        first = sentences[0]
        if len(first.strip()) < 8 and len(sentences) > 1:
            first = f"{first} {sentences[1]}"
        return self._trim_summary_item(first)

    def _summary_priority(self, item: str) -> int:
        normalized = self._normalize_for_summary(item)
        priority = 10
        if "mon hoc" in normalized or "第" in normalized:
            priority += 35
        if "tu vung" in normalized or "ことば" in normalized:
            priority += 45
        if "ngu phap" in normalized or "ぶんぽう" in normalized:
            priority += 45
        if "tro tu" in normalized or "phuong tien" in normalized or "cach thuc" in normalized:
            priority += 35
        if "cac nhom dong tu" in normalized or "dong tu dac biet" in normalized:
            priority += 35
        if "the て" in normalized or "ください" in normalized or "かた" in normalized:
            priority += 35
        if "わかります" in normalized or "どの" in normalized or "どれ" in normalized:
            priority += 30
        if "チャレンジ" in normalized or "challenge" in normalized:
            priority += 20
        if len(item) > 80:
            priority += 5
        return priority

    def _is_weak_summary_item(self, item: str) -> bool:
        normalized = self._normalize_for_summary(item)
        important_short_items = ["tu vung", "ことば", "ngu phap", "ぶんぽう"]
        if any(keyword in normalized for keyword in important_short_items):
            return False
        return len(item.strip()) < 18

    def _trim_summary_item(self, text: str, limit: int = 230) -> str:
        cleaned = " ".join((text or "").split())
        if len(cleaned) <= limit:
            return cleaned
        return cleaned[:limit].rstrip(" ,.;:") + "..."

    def _is_summary_question(self, question: str) -> bool:
        normalized = self._normalize_for_summary(question)
        return any(
            phrase in normalized
            for phrase in [
                "tong hop", "tom tat", "summary", "summarize", "noi dung chinh",
                "まとめ", "要約",
            ]
        )

    def _is_list_question(self, question: str) -> bool:
        normalized = self._normalize_for_summary(question)
        return any(
            phrase in normalized
            for phrase in [
                "tat ca",
                "toan bo",
                "liet ke",
                "danh sach",
                "tu vung",
                "ngu phap",
                "mau cau",
                "vi du",
                "bai tap",
                "gom nhung",
                "bao gom",
                "nhung gi",
                "cac loai",
                "cac buoc",
                "quy trinh",
                "語彙",
                "ことば",
                "文法",
                "ぶんぽう",
                "練習",
                "例文",
            ]
        )

    def _normalize_for_summary(self, text: str) -> str:
        without_marks = unicodedata.normalize("NFD", text or "")
        without_marks = "".join(char for char in without_marks if unicodedata.category(char) != "Mn")
        without_marks = without_marks.replace("đ", "d").replace("Đ", "D")
        return " ".join(without_marks.lower().split())


def build_sources(chunks: list[RetrievedChunk]) -> list[dict[str, Any]]:
    sources: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    for chunk in chunks:
        location = location_label(chunk)
        key = (chunk.filename, location)
        if key in seen:
            continue
        seen.add(key)
        sources.append(
            {
                "filename": chunk.filename,
                "document_id": chunk.document_id,
                "chunk_id": chunk.chunk_id,
                "subject": chunk.subject,
                "chapter": chunk.chapter,
                "page": chunk.page,
                "location": location,
                "score": round(chunk.score, 4),
                "semantic_score": round(chunk.semantic_score, 4),
                "lexical_score": round(chunk.lexical_score, 4),
                "preview": chunk.content[:280],
            }
        )
    return sources


def location_label(chunk: RetrievedChunk) -> str:
    if chunk.page:
        return f"trang {chunk.page}"
    if chunk.chapter and chunk.chapter != "Chưa phân chương":
        return f"chương {chunk.chapter}"
    return "không rõ trang"


def format_source(chunk: RetrievedChunk) -> str:
    return f"{chunk.filename}, {location_label(chunk)}"
