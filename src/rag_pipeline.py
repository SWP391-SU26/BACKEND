from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

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
        if provider in {"auto", "openai"} and self.settings.openai_api_key:
            try:
                return self._generate_with_openai(question, contexts)
            except Exception:
                return self._generate_extractive_answer(question, contexts, sources)
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
            )
            return self.local_generator
        except Exception as exc:
            self.local_generator_error = str(exc)
            return None

    def generation_status(self) -> dict[str, Any]:
        provider = self.settings.generation_provider.lower().strip()
        return {
            "configured_provider": provider,
            "adapter_path": str(self.settings.lora_adapter_dir),
            "adapter_exists": self.settings.lora_adapter_dir.exists(),
            "local_model_loaded": self.local_generator is not None,
            "local_model_error": self.local_generator_error,
            "openai_configured": bool(self.settings.openai_api_key),
        }

    def generate_without_retrieval(self, question: str) -> str:
        generator = self._get_local_generator()
        if not generator:
            raise RuntimeError(self.local_generator_error or "Local LoRA model chưa sẵn sàng.")
        return generator.generate_without_context(question)

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
        query_terms = set(tokenize(question))
        candidates: list[tuple[float, str, RetrievedChunk]] = []
        for chunk in contexts:
            for sentence in split_sentences(chunk.content):
                terms = set(tokenize(sentence))
                if not terms:
                    continue
                overlap = len(query_terms & terms) / max(1, len(query_terms))
                score = overlap + (chunk.score * 0.25)
                candidates.append((score, sentence, chunk))

        candidates.sort(key=lambda item: item[0], reverse=True)
        selected = [sentence for score, sentence, _chunk in candidates[:4] if score > 0]
        if not selected:
            return OUT_OF_SCOPE_MESSAGE

        source_text = "; ".join(
            f"[{source['filename']}, {source['location']}]" for source in sources[:3]
        )
        body = " ".join(selected)
        return f"Dựa trên tài liệu, {body}\n\nNguồn: {source_text}"


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
