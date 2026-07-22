from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any
import importlib.util
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
        conversation_history: list[dict[str, str]] | None = None,
    ) -> str:
        provider = self.settings.generation_provider.lower().strip()
        if strict or provider in {"auto", "lora", "local"}:
            generator = self._get_local_generator()
            if generator:
                try:
                    answer = generator.generate(question, contexts, conversation_history or [])
                    if answer:
                        return answer
                except Exception as exc:
                    self.local_generator_error = str(exc)
                    if strict:
                        raise RuntimeError(f"Local LoRA generation failed: {exc}") from exc
        if (strict or provider in {"auto", "openai"}) and self.settings.openai_api_key:
            try:
                return self._generate_with_openai(question, contexts, conversation_history or [])
            except Exception as exc:
                if strict:
                    raise RuntimeError(f"OpenAI generation failed: {exc}") from exc
                return self._generate_extractive_answer(
                    self._extractive_question(question, conversation_history), contexts, sources
                )
        if strict:
            raise RuntimeError(self.local_generator_error or "No strict generation model is ready.")
        return self._generate_extractive_answer(
            self._extractive_question(question, conversation_history), contexts, sources
        )

    def _extractive_question(
        self,
        question: str,
        conversation_history: list[dict[str, str]] | None,
    ) -> str:
        normalized = self._normalize_for_summary(question).replace("đ", "d")
        follow_up_markers = {
            "giai thich them", "noi ro hon", "vi sao", "tai sao", "no la gi",
            "no co tac dung gi", "cai nay", "phan nay", "the con", "them vi du",
        }
        is_follow_up = any(marker in normalized for marker in follow_up_markers)
        if not is_follow_up:
            return question

        previous_questions = [
            item.get("content", "").strip()
            for item in conversation_history or []
            if item.get("role") == "user" and item.get("content", "").strip()
        ]
        return previous_questions[-1] if previous_questions else question

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
            )
            return self.local_generator
        except Exception as exc:
            self.local_generator_error = str(exc)
            return None

    def generation_status(self) -> dict[str, Any]:
        provider = self.settings.generation_provider.lower().strip()
        required_modules = {name: importlib.util.find_spec(name) is not None for name in ("torch", "transformers", "peft")}
        adapter_files = ["adapter_config.json", "adapter_model.safetensors", "tokenizer_config.json"]
        adapter_ready = self.settings.lora_adapter_dir.is_dir() and all(
            (self.settings.lora_adapter_dir / name).exists() for name in adapter_files
        )
        configured_ready = all(required_modules.values()) and adapter_ready and bool(self.settings.local_base_model)
        inference_ready = configured_ready and self.local_generator is not None \
            and self.local_generator.warmed_up and self.local_generator_error is None
        generation_ready = inference_ready or bool(self.settings.openai_api_key)
        return {
            "configured_provider": provider,
            "adapter_dir": str(self.settings.lora_adapter_dir),
            "adapter_exists": self.settings.lora_adapter_dir.exists(),
            "adapter_ready": adapter_ready,
            "configured_ready": configured_ready,
            "base_model": self.settings.local_base_model,
            "dependencies": required_modules,
            "inference_ready": inference_ready,
            "training_ready": False,
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

    def generate_without_retrieval_batch(self, questions: list[str]) -> list[str]:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model is not ready.")
        return generator.generate_without_context_batch(
            questions,
            max_new_tokens=self.settings.benchmark_max_new_tokens,
            max_input_tokens=self.settings.benchmark_max_input_tokens,
        )

    def generate_without_retrieval(self, question: str) -> str:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model chưa sẵn sàng.")
        return generator.generate_without_context(question)

    def _generate_with_openai(
        self,
        question: str,
        contexts: list[RetrievedChunk],
        conversation_history: list[dict[str, str]],
    ) -> str:
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
        history = [
            {"role": item["role"], "content": item["content"]}
            for item in conversation_history[-6:]
            if item.get("role") in {"user", "assistant"} and item.get("content")
        ]
        response = client.chat.completions.create(
            model=self.settings.openai_chat_model,
            temperature=0.1,
            messages=[
                {"role": "system", "content": system_prompt},
                *history,
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

        query_terms = set(tokenize(question))
        normalized_question = self._normalize_for_summary(question).replace("đ", "d")
        candidates: list[tuple[float, str, RetrievedChunk]] = []
        for chunk in contexts:
            for sentence in split_sentences(chunk.content):
                terms = set(tokenize(sentence))
                if not terms:
                    continue
                normalized_sentence = self._normalize_for_summary(sentence).replace("đ", "d")
                if "thu do" in normalized_question and "thu do" not in normalized_sentence:
                    continue
                overlap = len(query_terms & terms) / max(1, len(query_terms))
                if overlap <= 0:
                    continue
                score = overlap + (chunk.score * 0.25)
                candidates.append((score, sentence, chunk))

        candidates.sort(key=lambda item: item[0], reverse=True)
        selected_candidates = candidates[:1]
        if not selected_candidates:
            return OUT_OF_SCOPE_MESSAGE

        selected: list[str] = []
        selected_chunks: list[RetrievedChunk] = []
        seen_chunk_ids: set[str] = set()
        seen_text: set[str] = set()
        for _score, sentence, chunk in selected_candidates:
            excerpt = " ".join(sentence.split())
            if len(excerpt) > 280:
                excerpt = excerpt[:277].rsplit(" ", 1)[0].rstrip(" ,;:") + "..."
            excerpt_key = " ".join(tokenize(excerpt))
            if not excerpt_key or excerpt_key in seen_text:
                continue
            seen_text.add(excerpt_key)
            selected.append(excerpt)
            if chunk.chunk_id in seen_chunk_ids:
                continue
            seen_chunk_ids.add(chunk.chunk_id)
            selected_chunks.append(chunk)

        if not selected:
            return OUT_OF_SCOPE_MESSAGE
        if len(selected) == 1:
            return f"Theo tài liệu: {selected[0]}"
        body = "\n".join(f"- {item}" for item in selected)
        return f"Theo tài liệu:\n{body}"

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
        if len(normalized) < 2:
            return True
        return normalized in {"mon hoc", "giang vien"}

    def _list_answer_label(self, question: str) -> str:
        normalized = self._normalize_for_summary(question)
        if "tu vung" in normalized:
            return "danh sách từ vựng tìm thấy"
        if "ngu phap" in normalized or "mau cau" in normalized:
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
            for phrase in ["tong hop", "tom tat", "summary", "summarize", "noi dung chinh"]
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
            ]
        )

    def _normalize_for_summary(self, text: str) -> str:
        without_marks = unicodedata.normalize("NFD", text or "")
        without_marks = "".join(char for char in without_marks if unicodedata.category(char) != "Mn")
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
