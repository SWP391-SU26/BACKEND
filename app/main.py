from __future__ import annotations

import shutil
import sys
import importlib.util
import json
import re
import time
from dataclasses import asdict, replace
from datetime import datetime
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, Form, HTTPException, Response, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.config import ensure_data_dirs, load_settings
from src.document_loader import SUPPORTED_EXTENSIONS
from src.embeddings import get_embedding_provider
from src.evaluation import BenchmarkRunner
from src.finetuning import (
    FINETUNED_REFUSAL_MESSAGE,
    is_refusal_answer,
    prepare_dataset,
    selected_sources_are_trained,
    training_source_names,
    validate_jsonl,
)
from src.job_manager import BackgroundJobManager
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore
from src.text_utils import safe_filename, tokenize


class SessionCreateRequest(BaseModel):
    title: str = Field(default="Phiên chat mới", max_length=120)


class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    session_id: str | None = None
    subject: str | None = None


class ChatResponse(BaseModel):
    session_id: str
    answer: str
    sources: list[dict[str, Any]]
    retrieved: list[dict[str, Any]]


class BenchmarkRequest(BaseModel):
    test_set_path: str = "data/test_set.csv"
    mode: str = "rag"
    generation_provider: str = "auto"


class FineTuningPrepareRequest(BaseModel):
    source_csv: str = "data/test_set.csv"
    validation_ratio: float = Field(default=0.2, ge=0, lt=1)
    seed: int = 42

class GenerateContext(BaseModel):
    chunk_id: str
    document_id: str
    filename: str
    page: int | None = None
    content: str
    score: float = 1.0


class ChatHistoryItem(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=4000)


class GenerateRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    contexts: list[GenerateContext]
    strict: bool = False
    standalone_query: str | None = Field(default=None, max_length=4000)
    history: list[ChatHistoryItem] = Field(default_factory=list, max_length=12)
    answer_profile: str = Field(default="default", max_length=40)


class RewriteQueryRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    history: list[ChatHistoryItem] = Field(default_factory=list, max_length=12)
    intent: str = Field(default="factual", max_length=40)
    attempt: int = Field(default=1, ge=1, le=2)
    evidence_hints: list[str] = Field(default_factory=list, max_length=2)


class RewriteQueryResponse(BaseModel):
    standalone_query: str
    language: str
    intent: str
    attempt: int
    base_model: str

class GenerateSource(BaseModel):
    chunk_id: str
    document_id: str
    filename: str
    page: int | None = None
    location: str | None = None
    preview: str
    score: float | None = None

class GenerateResponse(BaseModel):
    answer: str
    is_out_of_scope: bool
    sources: list[GenerateSource]
    provider_used: str
    base_model: str
    adapter_version: str | None = None
    embedding_model: str
    generation_mode: str
    dataset_version: str
    prompt_version: str
    used_chunk_ids: list[str] = Field(default_factory=list)
    peak_vram_bytes: int = 0
    grounding_status: str = "GROUNDED"
    fallback_reason: str | None = None
    grounding_score: float = 0.0
    repair_attempted: bool = False
    unsupported_sentence_count: int = 0


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=64)


class EmbedResponse(BaseModel):
    provider: str
    model: str
    dimension: int
    vectors: list[list[float]]

class GenerateBatchItem(BaseModel):
    request_id: str = Field(min_length=1, max_length=100)
    question: str = Field(min_length=1, max_length=4000)
    contexts: list[GenerateContext]
    standalone_query: str | None = Field(default=None, max_length=4000)
    history: list[ChatHistoryItem] = Field(default_factory=list, max_length=12)
    answer_profile: str = Field(default="default", max_length=40)

class GenerateBatchRequest(BaseModel):
    items: list[GenerateBatchItem] = Field(min_length=1, max_length=16)
    strict: bool = True

class GenerateBatchResult(BaseModel):
    request_id: str
    answer: str | None = None
    is_out_of_scope: bool = False
    sources: list[GenerateSource] = Field(default_factory=list)
    error: str | None = None
    provider_used: str = "local-base"
    base_model: str | None = None
    adapter_version: str | None = None
    embedding_model: str | None = None
    generation_mode: str = "BASE_RAG"
    dataset_version: str | None = None
    prompt_version: str | None = None
    used_chunk_ids: list[str] = Field(default_factory=list)
    peak_vram_bytes: int = 0
    grounding_status: str = "GROUNDED"
    fallback_reason: str | None = None
    grounding_score: float = 0.0
    repair_attempted: bool = False
    unsupported_sentence_count: int = 0

class GenerateBatchResponse(BaseModel):
    items: list[GenerateBatchResult]
    batch_size: int
    max_input_tokens: int
    max_new_tokens: int

class ChatFinetunedRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    strict: bool = True
    document_filenames: list[str] = Field(default_factory=list)

class ChatFinetunedResponse(BaseModel):
    answer: str
    is_out_of_scope: bool = False
    scope_confidence: float | None = None
    model_ready: bool = True
    status_code: str | None = None
    provider_used: str = "local-lora"
    base_model: str | None = None
    adapter_version: str | None = None
    generation_mode: str = "FINE_TUNED_ONLY"
    dataset_version: str | None = None
    prompt_version: str | None = None
    peak_vram_bytes: int = 0
    verification_status: str = "VERIFIED"
    quality_gate_passed: bool = False

class ChatFinetunedBatchItem(BaseModel):
    request_id: str = Field(min_length=1, max_length=100)
    question: str = Field(min_length=1, max_length=4000)
    document_filenames: list[str] = Field(default_factory=list)

class ChatFinetunedBatchRequest(BaseModel):
    items: list[ChatFinetunedBatchItem] = Field(min_length=1, max_length=16)
    strict: bool = True
    allow_unverified: bool = False

class ChatFinetunedBatchResult(BaseModel):
    request_id: str
    answer: str | None = None
    error: str | None = None
    is_out_of_scope: bool = False
    scope_confidence: float | None = None
    provider_used: str = "local-lora"
    base_model: str | None = None
    adapter_version: str | None = None
    generation_mode: str = "FINE_TUNED_ONLY"
    dataset_version: str | None = None
    prompt_version: str | None = None
    peak_vram_bytes: int = 0
    verification_status: str = "VERIFIED"
    quality_gate_passed: bool = False

class ChatFinetunedBatchResponse(BaseModel):
    items: list[ChatFinetunedBatchResult]
    batch_size: int
    max_input_tokens: int
    max_new_tokens: int

class EvaluateRequest(BaseModel):
    question: str
    answer_rag: str
    answer_finetuned: str

class EvaluateResponse(BaseModel):
    evaluation: str


class OfficialRagasItem(BaseModel):
    request_id: str = Field(min_length=1, max_length=100)
    question: str = Field(min_length=1, max_length=4000)
    response: str = Field(min_length=1)
    reference: str = Field(min_length=1)
    contexts: list[str] = Field(default_factory=list, max_length=12)


class OfficialRagasBatchRequest(BaseModel):
    items: list[OfficialRagasItem] = Field(min_length=1, max_length=16)


class OfficialRagasResult(BaseModel):
    request_id: str
    faithfulness: float | None = None
    answer_relevancy: float
    context_precision: float | None = None
    context_recall: float | None = None
    judge_model: str
    embedding_model: str
    prompt_version: str


class OfficialRagasBatchResponse(BaseModel):
    metric_standard: str = "RAGAS_OFFICIAL"
    judge_model: str
    evaluator_embedding: str
    prompt_version: str
    items: list[OfficialRagasResult]


def build_pipeline() -> tuple[RAGPipeline, SQLiteStore]:
    settings = load_settings()
    ensure_data_dirs(settings)
    store = SQLiteStore(settings.db_path)
    embedding_provider = get_embedding_provider(settings)
    return RAGPipeline(settings, store, embedding_provider), store


def select_sources_for_answer(answer: str, sources: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not answer or not sources:
        return sources[:12]

    sources_in_answer: list[tuple[int, dict[str, Any]]] = []
    for source in sources:
        label = f"[{source.get('filename')}, {source.get('location')}]"
        position = answer.find(label)
        if position >= 0:
            sources_in_answer.append((position, source))
    if sources_in_answer:
        sources_in_answer.sort(key=lambda item: item[0])
        return [source for _position, source in sources_in_answer[:12]]

    answer_terms = set(tokenize(answer))
    ranked: list[tuple[int, int, dict[str, Any]]] = []
    for index, source in enumerate(sources):
        preview = str(source.get("preview") or "")
        preview_terms = set(tokenize(preview))
        score = len(answer_terms & preview_terms)
        if preview and preview[:80] in answer:
            score += 20
        ranked.append((score, index, source))

    ranked.sort(key=lambda item: (-item[0], item[1]))
    selected = [source for score, _index, source in ranked[:12] if score > 0]
    return selected


pipeline, store = build_pipeline()
benchmark_runner = BenchmarkRunner(pipeline, store)
job_manager = BackgroundJobManager(max_workers=1)
official_ragas_evaluator = None


def fine_tuned_response_metadata() -> dict[str, Any]:
    status = pipeline.generation_status()
    metadata = pipeline.fine_tuned_metadata()
    return {
        "provider_used": "local-lora",
        "base_model": pipeline.settings.local_base_model,
        "adapter_version": status.get("adapter_version")
        or pipeline.settings.lora_adapter_dir.name,
        "generation_mode": "FINE_TUNED_ONLY",
        "dataset_version": status.get("dataset_version")
        or pipeline.settings.dataset_version,
        "prompt_version": pipeline.settings.prompt_version,
        "peak_vram_bytes": metadata.peak_vram_bytes,
        "verification_status": status.get("model_verification_status", "UNVERIFIED"),
        "quality_gate_passed": bool(
            (status.get("quality_gate") or {}).get("passed")
        ),
    }

app = FastAPI(
    title="RAG Chatbot API",
    description="REST API cho frontend Java kết nối chatbot hỏi đáp tài liệu môn học.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def root() -> dict[str, str]:
    return {
        "name": "RAG Chatbot API",
        "status": "ok",
        "docs": "/docs",
        "health": "/api/health",
    }


@app.get("/favicon.ico", include_in_schema=False)
def favicon() -> Response:
    return Response(status_code=204)


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/model/status")
def model_status() -> dict[str, Any]:
    provider = pipeline.settings.generation_provider.lower().strip()
    if provider in {"auto", "lora", "local"}:
        try:
            pipeline.warmup_local_model()
        except Exception:
            pass
    generation = pipeline.generation_status()
    return {
        "embedding_model": pipeline.embedding_provider.model,
        "generation": generation,
        "inference_ready": generation["inference_ready"],
        "training_ready": generation["training_ready"],
        "generation_ready": generation["generation_ready"],
        "adapter_dir": generation["adapter_dir"],
        "base_rag_status": generation["base_rag_status"],
        "fine_tuned_status": generation["fine_tuned_status"],
        "dataset_version": generation["dataset_version"],
        "quantization": generation["quantization"],
        "generation_device": generation["generation_device"],
        "embedding_device": generation["embedding_device"],
        "shared_runtime_loaded": generation["shared_runtime_loaded"],
        "adapter_verified": generation["adapter_verified"],
        "benchmark_eligible": generation["benchmark_eligible"],
    }


@app.post("/api/embed", response_model=EmbedResponse)
def embed_texts(request: EmbedRequest) -> EmbedResponse:
    vectors = pipeline.embedding_provider.embed_texts(request.texts)
    dimension = len(vectors[0]) if vectors else 0
    return EmbedResponse(
        provider=pipeline.embedding_provider.name,
        model=pipeline.embedding_provider.model,
        dimension=dimension,
        vectors=vectors,
    )


@app.post("/api/rewrite-query", response_model=RewriteQueryResponse)
def rewrite_query(request: RewriteQueryRequest) -> RewriteQueryResponse:
    try:
        standalone = pipeline.rewrite_query(
            request.question,
            history=[item.model_dump() for item in request.history],
            intent=request.intent,
            attempt=request.attempt,
            evidence_hints=request.evidence_hints,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "QUERY_REWRITE_UNAVAILABLE", "message": str(exc)},
        ) from exc
    vietnamese_pattern = (
        r"[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệ"
        r"íìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]"
    )
    return RewriteQueryResponse(
        standalone_query=standalone,
        language="vi" if re.search(vietnamese_pattern, request.question.casefold()) else "unknown",
        intent=request.intent,
        attempt=request.attempt,
        base_model=pipeline.settings.local_base_model,
    )


@app.get("/api/documents")
def list_documents() -> list[dict[str, Any]]:
    return store.list_documents()


@app.get("/api/subjects")
def list_subjects() -> list[str]:
    return store.list_subjects()


@app.post("/api/documents")
def upload_document(
    file: UploadFile = File(...),
    subject: str = Form(default="Môn học demo"),
    chapter: str = Form(default="Chung"),
) -> dict[str, Any]:
    suffix = Path(file.filename or "").suffix.lower()
    if suffix not in SUPPORTED_EXTENSIONS:
        allowed = ", ".join(sorted(SUPPORTED_EXTENSIONS))
        raise HTTPException(status_code=400, detail=f"File type không hỗ trợ. Cho phép: {allowed}")

    settings = load_settings()
    ensure_data_dirs(settings)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"{timestamp}_{safe_filename(file.filename or 'document')}"
    destination = settings.raw_dir / filename

    try:
        with destination.open("wb") as handle:
            shutil.copyfileobj(file.file, handle)
        result = pipeline.ingest_file(destination, subject=subject, chapter=chapter)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không thể xử lý tài liệu: {exc}") from exc
    finally:
        file.file.close()

    return {
        "document_id": result.document_id,
        "filename": result.filename,
        "num_pages": result.num_pages,
        "num_chunks": result.num_chunks,
    }


@app.delete("/api/documents/{document_id}")
def delete_document(document_id: str) -> dict[str, str]:
    store.delete_document(document_id)
    return {"status": "deleted", "document_id": document_id}


@app.get("/api/sessions")
def list_sessions() -> list[dict[str, Any]]:
    return store.list_sessions()


@app.post("/api/sessions")
def create_session(request: SessionCreateRequest) -> dict[str, str]:
    session_id = store.create_session(request.title.strip() or "Phiên chat mới")
    return {"session_id": session_id}


@app.get("/api/sessions/{session_id}/messages")
def list_messages(session_id: str) -> list[dict[str, Any]]:
    return store.list_messages(session_id)


@app.post("/api/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    session_id = request.session_id or store.create_session("Phiên chat API")
    try:
        result = pipeline.answer(session_id, request.question, subject=request.subject)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không thể tạo câu trả lời: {exc}") from exc

    retrieved = [
        {
            "chunk_id": chunk.chunk_id,
            "document_id": chunk.document_id,
            "filename": chunk.filename,
            "subject": chunk.subject,
            "chapter": chunk.chapter,
            "page": chunk.page,
            "score": round(chunk.score, 4),
            "semantic_score": round(chunk.semantic_score, 4),
            "lexical_score": round(chunk.lexical_score, 4),
            "content": chunk.content,
        }
        for chunk in result.retrieved
    ]
    return ChatResponse(
        session_id=session_id,
        answer=result.answer,
        sources=result.sources,
        retrieved=retrieved,
    )

@app.post("/api/generate", response_model=GenerateResponse)
def generate_answer(request: GenerateRequest) -> GenerateResponse:
    from src.grounded_answer import (
        answer_is_complete,
        answer_is_well_formed,
        ensure_grounded_answer,
        select_context_windows,
    )
    from src.rag_pipeline import OUT_OF_SCOPE_MESSAGE, location_label
    from src.storage import RetrievedChunk
    
    if not request.contexts:
        return GenerateResponse(
            answer=OUT_OF_SCOPE_MESSAGE,
            is_out_of_scope=True,
            sources=[],
            provider_used="scope-guard",
            base_model=pipeline.settings.local_base_model,
            embedding_model=pipeline.embedding_provider.model,
            generation_mode="OUT_OF_SCOPE",
            dataset_version=pipeline.settings.dataset_version,
            prompt_version=pipeline.settings.prompt_version,
            used_chunk_ids=[],
            peak_vram_bytes=0,
            grounding_status="OUT_OF_SCOPE",
            fallback_reason="NO_RELEVANT_CONTEXT",
        )
        
    contexts = []
    sources_dict_list = []
    
    for ctx in request.contexts:
        chunk = RetrievedChunk(
            chunk_id=ctx.chunk_id,
            document_id=ctx.document_id,
            filename=ctx.filename,
            subject="Unknown",
            chapter="Unknown",
            page=ctx.page,
            content=ctx.content,
            score=ctx.score,
            semantic_score=ctx.score,
            lexical_score=1.0
        )
        contexts.append(chunk)
        sources_dict_list.append({
            "chunk_id": ctx.chunk_id,
            "document_id": ctx.document_id,
            "filename": ctx.filename,
            "page": ctx.page,
            "location": location_label(chunk),
            "preview": ctx.content[:280],
            "score": ctx.score,
        })
        
    contexts = select_context_windows(
        request.standalone_query or request.question,
        contexts,
        answer_profile=request.answer_profile,
    )
    if not contexts:
        return GenerateResponse(
            answer=OUT_OF_SCOPE_MESSAGE,
            is_out_of_scope=True,
            sources=[],
            provider_used="scope-guard",
            base_model=pipeline.settings.local_base_model,
            embedding_model=pipeline.embedding_provider.model,
            generation_mode="OUT_OF_SCOPE",
            dataset_version=pipeline.settings.dataset_version,
            prompt_version=pipeline.settings.prompt_version,
            grounding_status="OUT_OF_SCOPE",
            fallback_reason="NO_RELEVANT_CONTEXT",
        )

    started_at = time.perf_counter()
    output_tokens = {
        "definition": 180,
        "factual": 180,
        "comparison": 240,
        "list": 220,
        "reasoning": 240,
        "procedure": 220,
        "summary": 320,
    }.get(request.answer_profile, 180)
    try:
        generated = pipeline.generate_base_rag_answer(
            request.question,
            contexts,
            history=[item.model_dump() for item in request.history],
            standalone_query=request.standalone_query,
            answer_profile=request.answer_profile,
            strict_prompt=True,
            max_input_tokens=2048,
            max_new_tokens=output_tokens,
            max_time_seconds=32,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không thể tạo câu trả lời: {exc}") from exc

    grounded = ensure_grounded_answer(
        request.question,
        generated.answer,
        contexts,
        embedding_provider=pipeline.embedding_provider,
    )
    repair_attempted = (
        (
            bool(grounded.unsupported_sentences)
            or not answer_is_complete(grounded.answer, request.answer_profile)
        )
        and (time.perf_counter() - started_at) < 30
    )
    if repair_attempted:
        try:
            repaired = pipeline.complete_grounded_answer(
                request.question,
                grounded.answer,
                contexts,
                answer_profile=request.answer_profile,
                max_input_tokens=2048,
                max_new_tokens=min(output_tokens, 240),
                max_time_seconds=15,
            )
            repaired_grounding = ensure_grounded_answer(
                request.question,
                repaired.answer,
                contexts,
                embedding_provider=pipeline.embedding_provider,
            )
            repaired_is_better = (
                repaired_grounding.answer
                and answer_is_well_formed(repaired_grounding.answer)
                and repaired_grounding.support_score
                >= max(0.44, grounded.support_score - 0.02)
                and (
                    answer_is_complete(repaired_grounding.answer, request.answer_profile)
                    or len(repaired_grounding.answer) > len(grounded.answer)
                )
            )
            if repaired_is_better:
                grounded = repaired_grounding
                generated = repaired
        except Exception:
            pass
    answer = (
        grounded.answer
        if answer_is_well_formed(grounded.answer)
        else OUT_OF_SCOPE_MESSAGE
    )
    if answer == OUT_OF_SCOPE_MESSAGE:
        grounded = replace(
            grounded,
            answer="",
            used_chunk_ids=[],
            used_fallback=True,
        )
    used_ids = set(grounded.used_chunk_ids)
    selected_sources = [
        source for source in sources_dict_list if source["chunk_id"] in used_ids
    ]
    provider_used = generated.provider_used
    grounding_status = (
        "OUT_OF_SCOPE"
        if not grounded.answer
        else ("PARTIAL_GROUNDED" if grounded.unsupported_sentence_count else "GROUNDED")
    )
        
    return GenerateResponse(
        answer=answer,
        is_out_of_scope=(answer == OUT_OF_SCOPE_MESSAGE),
        sources=[GenerateSource(**s) for s in selected_sources],
        provider_used=provider_used,
        base_model=generated.base_model,
        adapter_version=generated.adapter_version,
        embedding_model=pipeline.embedding_provider.model,
        generation_mode=generated.generation_mode,
        dataset_version=generated.dataset_version,
        prompt_version=generated.prompt_version,
        used_chunk_ids=grounded.used_chunk_ids,
        peak_vram_bytes=generated.peak_vram_bytes,
        grounding_status=grounding_status,
        fallback_reason=(
            "GROUNDING_FAILED" if not grounded.answer else None
        ),
        grounding_score=grounded.support_score,
        repair_attempted=repair_attempted,
        unsupported_sentence_count=grounded.unsupported_sentence_count,
    )


def _to_retrieved_contexts(contexts: list[GenerateContext]):
    from src.rag_pipeline import location_label
    from src.storage import RetrievedChunk

    retrieved = []
    sources = []
    for ctx in contexts:
        chunk = RetrievedChunk(
            chunk_id=ctx.chunk_id,
            document_id=ctx.document_id,
            filename=ctx.filename,
            subject="Unknown",
            chapter="Unknown",
            page=ctx.page,
            content=ctx.content,
            score=ctx.score,
            semantic_score=ctx.score,
            lexical_score=1.0,
        )
        retrieved.append(chunk)
        sources.append({
            "chunk_id": ctx.chunk_id,
            "document_id": ctx.document_id,
            "filename": ctx.filename,
            "page": ctx.page,
            "location": location_label(chunk),
            "preview": ctx.content[:280],
            "score": ctx.score,
        })
    return retrieved, sources


@app.post("/api/generate-batch", response_model=GenerateBatchResponse)
def generate_answer_batch(request: GenerateBatchRequest) -> GenerateBatchResponse:
    from src.rag_pipeline import OUT_OF_SCOPE_MESSAGE

    settings = load_settings()
    if len(request.items) > settings.benchmark_batch_size:
        raise HTTPException(
            status_code=400,
            detail=f"Batch contains {len(request.items)} items; maximum is {settings.benchmark_batch_size}.",
        )

    from src.grounded_answer import select_context_windows

    prepared = []
    source_maps: dict[str, dict[str, dict[str, Any]]] = {}
    for item in request.items:
        contexts, sources = _to_retrieved_contexts(item.contexts)
        contexts = select_context_windows(
            item.standalone_query or item.question,
            contexts,
            answer_profile=item.answer_profile,
        )
        prepared.append((
            item.question,
            contexts,
            item.standalone_query or item.question,
            item.answer_profile,
        ))
        source_maps[item.request_id] = {source["chunk_id"]: source for source in sources}

    try:
        generated = pipeline.generate_rag_batch(prepared)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Batch RAG generation failed: {exc}") from exc

    from src.grounded_answer import ensure_grounded_answer

    results = []
    for item, (answer, included_contexts) in zip(request.items, generated):
        grounded = ensure_grounded_answer(
            item.question,
            answer,
            included_contexts,
            embedding_provider=pipeline.embedding_provider,
        )
        repair_attempted = bool(grounded.unsupported_sentences)
        if repair_attempted:
            try:
                repaired = pipeline.repair_grounding_answer(
                    item.question,
                    grounded.unsupported_sentences,
                    included_contexts,
                    max_input_tokens=settings.benchmark_max_input_tokens,
                    max_new_tokens=settings.benchmark_max_new_tokens,
                )
                candidate = " ".join(
                    part for part in (grounded.answer, repaired.answer) if part
                ).strip()
                repaired_grounding = ensure_grounded_answer(
                    item.question,
                    candidate,
                    included_contexts,
                    embedding_provider=pipeline.embedding_provider,
                )
                if repaired_grounding.answer:
                    grounded = repaired_grounding
            except Exception:
                pass
        normalized = grounded.answer.strip() if grounded.answer else OUT_OF_SCOPE_MESSAGE
        used_ids = set(grounded.used_chunk_ids)
        included_sources = [
            source_maps[item.request_id].get(context.chunk_id)
            for context in included_contexts
            if context.chunk_id in used_ids
            and source_maps[item.request_id].get(context.chunk_id) is not None
        ]
        selected_sources = select_sources_for_answer(normalized, included_sources)
        results.append(GenerateBatchResult(
            request_id=item.request_id,
            answer=normalized,
            is_out_of_scope=normalized == OUT_OF_SCOPE_MESSAGE,
            sources=[GenerateSource(**source) for source in selected_sources],
            provider_used="local-base",
            base_model=pipeline.settings.local_base_model,
            embedding_model=pipeline.embedding_provider.model,
            generation_mode="BASE_RAG",
            dataset_version=pipeline.settings.dataset_version,
            prompt_version=pipeline.settings.prompt_version,
            used_chunk_ids=[source["chunk_id"] for source in selected_sources],
            peak_vram_bytes=pipeline._generation_output(
                "", provider_used="local-base", generation_mode="BASE_RAG"
            ).peak_vram_bytes,
            grounding_status=(
                "OUT_OF_SCOPE"
                if normalized == OUT_OF_SCOPE_MESSAGE
                else ("PARTIAL_GROUNDED" if grounded.unsupported_sentence_count else "GROUNDED")
            ),
            fallback_reason=(
                "GROUNDING_FAILED" if not grounded.answer else None
            ),
            grounding_score=grounded.support_score,
            repair_attempted=repair_attempted,
            unsupported_sentence_count=grounded.unsupported_sentence_count,
        ))
    return GenerateBatchResponse(
        items=results,
        batch_size=len(results),
        max_input_tokens=settings.benchmark_max_input_tokens,
        max_new_tokens=settings.benchmark_max_new_tokens,
    )

@app.post("/ai/chat-finetuned", response_model=ChatFinetunedResponse)
def chat_finetuned(request: ChatFinetunedRequest) -> ChatFinetunedResponse:
    settings = load_settings()
    trained_sources = training_source_names([
        settings.finetuning_dir / "train.jsonl",
        settings.finetuning_dir / "validation.jsonl",
    ])
    if not selected_sources_are_trained(request.document_filenames, trained_sources):
        return ChatFinetunedResponse(
            answer=FINETUNED_REFUSAL_MESSAGE,
            is_out_of_scope=True,
            scope_confidence=0.0,
            **fine_tuned_response_metadata(),
        )
    scope = pipeline.assess_finetuned_scope(request.question, request.document_filenames)
    if request.strict and not scope.allowed:
        return ChatFinetunedResponse(
            answer=FINETUNED_REFUSAL_MESSAGE,
            is_out_of_scope=True,
            scope_confidence=scope.confidence,
            **fine_tuned_response_metadata(),
        )
    generation_status = pipeline.generation_status()
    if not generation_status.get("configured_ready"):
        quality_gate = generation_status.get("quality_gate") or {}
        if not quality_gate.get("passed"):
            metrics = generation_status.get("evaluation_metrics") or {}
            thresholds = quality_gate.get("behavioral_thresholds") or {}
            answer_f1 = metrics.get("behavioral_answer_token_f1")
            refusal_accuracy = metrics.get("behavioral_refusal_accuracy")
            metric_detail = ""
            if answer_f1 is not None or refusal_accuracy is not None:
                metric_detail = (
                    f" Kết quả hiện tại: answer F1={answer_f1 if answer_f1 is not None else 'N/A'} "
                    f"(yêu cầu >= {thresholds.get('min_answer_token_f1', 'N/A')}), "
                    f"refusal={refusal_accuracy if refusal_accuracy is not None else 'N/A'} "
                    f"(yêu cầu >= {thresholds.get('min_refusal_accuracy', 'N/A')})."
                )
            reason = (
                "Adapter fine-tuned đang cấu hình chưa có manifest kiểm định hoặc chưa đạt "
                "behavioral quality gate. Hệ thống đã chặn model để tránh trả lời sai."
                + metric_detail
            )
            code = "QUALITY_GATE_FAILED"
        else:
            reason = "Runtime fine-tuning offline chưa đủ dependency hoặc chưa nạp được model."
            code = "MODEL_RUNTIME_NOT_READY"
        return ChatFinetunedResponse(
            answer=f"Fine-tuned model chưa sẵn sàng: {reason}",
            is_out_of_scope=True,
            scope_confidence=scope.confidence,
            model_ready=False,
            status_code=code,
            **fine_tuned_response_metadata(),
        )
    try:
        answer = pipeline.generate_without_retrieval(
            request.question,
            allowed_sources=request.document_filenames,
            strict=request.strict,
        )
        refused = is_refusal_answer(answer)
        return ChatFinetunedResponse(
            answer=FINETUNED_REFUSAL_MESSAGE if refused else answer,
            is_out_of_scope=refused,
            scope_confidence=scope.confidence,
            model_ready=True,
            **fine_tuned_response_metadata(),
        )
    except Exception as exc:
        return ChatFinetunedResponse(
            answer=(
                "Fine-tuned model chưa sẵn sàng: không thể nạp hoặc chạy adapter local. "
                f"Chi tiết: {str(exc)[:240]}"
            ),
            is_out_of_scope=True,
            scope_confidence=scope.confidence,
            model_ready=False,
            status_code="MODEL_RUNTIME_NOT_READY",
            **fine_tuned_response_metadata(),
        )


@app.post("/ai/chat-finetuned-batch", response_model=ChatFinetunedBatchResponse)
def chat_finetuned_batch(request: ChatFinetunedBatchRequest) -> ChatFinetunedBatchResponse:
    settings = load_settings()
    if len(request.items) > settings.benchmark_batch_size:
        raise HTTPException(
            status_code=400,
            detail=f"Batch contains {len(request.items)} items; maximum is {settings.benchmark_batch_size}.",
        )
    settings = load_settings()
    trained_sources = training_source_names([
        settings.finetuning_dir / "train.jsonl",
        settings.finetuning_dir / "validation.jsonl",
    ])
    accepted: list[tuple[ChatFinetunedBatchItem, float]] = []
    refused: dict[str, ChatFinetunedBatchResult] = {}
    for item in request.items:
        if not selected_sources_are_trained(item.document_filenames, trained_sources):
            refused[item.request_id] = ChatFinetunedBatchResult(
                request_id=item.request_id,
                answer=FINETUNED_REFUSAL_MESSAGE,
                is_out_of_scope=True,
                scope_confidence=0.0,
                **fine_tuned_response_metadata(),
            )
            continue
        scope = pipeline.assess_finetuned_scope(item.question, item.document_filenames)
        if request.strict and not scope.allowed:
            refused[item.request_id] = ChatFinetunedBatchResult(
                request_id=item.request_id,
                answer=FINETUNED_REFUSAL_MESSAGE,
                is_out_of_scope=True,
                scope_confidence=scope.confidence,
                **fine_tuned_response_metadata(),
            )
            continue
        accepted.append((item, scope.confidence))
    try:
        answers = pipeline.generate_without_retrieval_batch(
            [item.question for item, _confidence in accepted],
            allowed_sources=[item.document_filenames for item, _confidence in accepted],
            strict=request.strict,
            allow_unverified=request.allow_unverified,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "MODEL_NOT_READY", "message": f"Fine-tuned batch failed: {exc}"},
        ) from exc
    return ChatFinetunedBatchResponse(
        items=[
            refused.get(item.request_id)
            or next(
                ChatFinetunedBatchResult(
                    request_id=accepted_item.request_id,
                    answer=FINETUNED_REFUSAL_MESSAGE if is_refusal_answer(answer) else answer.strip(),
                    is_out_of_scope=is_refusal_answer(answer),
                    scope_confidence=confidence,
                    **fine_tuned_response_metadata(),
                )
                for (accepted_item, confidence), answer in zip(accepted, answers)
                if accepted_item.request_id == item.request_id
            )
            for item in request.items
        ],
        batch_size=len(request.items),
        max_input_tokens=settings.benchmark_max_input_tokens,
        max_new_tokens=settings.benchmark_max_new_tokens,
    )

@app.post("/ai/evaluate", response_model=EvaluateResponse)
def evaluate_answers(request: EvaluateRequest) -> EvaluateResponse:
    raise HTTPException(
        status_code=410,
        detail={
            "code": "LEGACY_EVALUATION_REMOVED",
            "message": "Use /api/evaluation/ragas/batch for Official RAGAS.",
        },
    )


@app.post(
    "/api/evaluation/ragas/batch",
    response_model=OfficialRagasBatchResponse,
)
async def evaluate_official_ragas(
    request: OfficialRagasBatchRequest,
) -> OfficialRagasBatchResponse:
    global official_ragas_evaluator
    settings = load_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail={
                "code": "RAGAS_JUDGE_NOT_CONFIGURED",
                "message": "OPENAI_API_KEY is required for the Official RAGAS judge.",
            },
        )
    if official_ragas_evaluator is None:
        from src.ragas_evaluator import OfficialRagasEvaluator

        official_ragas_evaluator = OfficialRagasEvaluator(
            api_key=settings.openai_api_key,
            judge_model=settings.openai_chat_model,
            embedding_provider=pipeline.embedding_provider,
            prompt_version=settings.prompt_version,
        )
    results = []
    for item in request.items:
        scores = await official_ragas_evaluator.evaluate(
            question=item.question,
            response=item.response,
            contexts=item.contexts,
            reference=item.reference,
        )
        results.append(
            OfficialRagasResult(
                request_id=item.request_id,
                **asdict(scores),
            )
        )
    return OfficialRagasBatchResponse(
        judge_model=official_ragas_evaluator.judge_model,
        evaluator_embedding=official_ragas_evaluator.embedding_model,
        prompt_version=official_ragas_evaluator.prompt_version,
        items=results,
    )


@app.get("/api/benchmarks")
def list_benchmarks() -> list[dict[str, Any]]:
    return store.list_benchmark_runs()


@app.post("/api/benchmarks/run")
def run_benchmark(request: BenchmarkRequest) -> dict[str, Any]:
    try:
        runner, path = build_benchmark_runner(request)
        return runner.run(path, mode=request.mode)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không thể chạy benchmark: {exc}") from exc


@app.post("/api/benchmark-jobs")
def create_benchmark_job(request: BenchmarkRequest) -> dict[str, Any]:
    try:
        _runner, path = build_benchmark_runner(request)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    def execute() -> dict[str, Any]:
        runner, _ = build_benchmark_runner(request)
        return runner.run(path, mode=request.mode)

    return job_manager.submit("benchmark", execute)


@app.get("/api/benchmark-jobs")
def list_benchmark_jobs() -> list[dict[str, Any]]:
    return job_manager.list()


@app.get("/api/benchmark-jobs/{job_id}")
def get_benchmark_job(job_id: str) -> dict[str, Any]:
    job = job_manager.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Không tìm thấy benchmark job.")
    return job


@app.get("/api/benchmarks/{run_id}")
def get_benchmark(run_id: str) -> dict[str, Any]:
    result = store.get_benchmark_run(run_id)
    if not result:
        raise HTTPException(status_code=404, detail="Không tìm thấy benchmark run.")
    return result


@app.get("/api/dashboard/summary")
def dashboard_summary() -> dict[str, Any]:
    runs = store.list_benchmark_runs()
    latest_by_configuration: dict[str, dict[str, Any]] = {}
    for run in runs:
        metrics = run["metrics"]
        key = "|".join(
            [
                run["embedding_model"],
                str(metrics.get("benchmark_mode", "legacy")),
                str(metrics.get("generation_provider", "legacy")),
            ]
        )
        if key not in latest_by_configuration:
            latest_by_configuration[key] = run
    return {
        "documents": {
            "total": len(store.list_documents()),
            "subjects": store.list_subjects(),
        },
        "models": model_status(),
        "latest_benchmarks": list(latest_by_configuration.values()),
        "benchmark_history": runs,
        "active_jobs": [
            job for job in job_manager.list() if job["status"] in {"queued", "running"}
        ],
        "metric_note": (
            "faithfulness_proxy, answer_relevancy_proxy, context_precision_proxy và "
            "context_recall_proxy là metric local không dùng LLM judge, không phải RAGAS chính thức."
        ),
    }


@app.get("/api/dashboard/comparison")
def dashboard_comparison() -> dict[str, Any]:
    rows = []
    seen: set[str] = set()
    for run in store.list_benchmark_runs():
        metrics = run["metrics"]
        if "benchmark_mode" not in metrics:
            continue
        key = f"{metrics['benchmark_mode']}|{metrics.get('generation_provider', 'unknown')}"
        if key in seen:
            continue
        seen.add(key)
        rows.append(
            {
                "configuration": key,
                "run_id": run["id"],
                "embedding_model": run["embedding_model"],
                **metrics,
                "quality_score": benchmark_quality_score(metrics),
            }
        )
    rows.sort(key=lambda item: item["quality_score"], reverse=True)
    return {
        "recommended": rows[0]["configuration"] if rows else None,
        "configurations": rows,
    }


@app.get("/api/evaluation/capabilities")
def evaluation_capabilities() -> dict[str, Any]:
    settings = load_settings()
    return {
        "official_ragas_enabled": bool(settings.openai_api_key),
        "metric_standard": "OFFICIAL_RAGAS",
        "judge_model": settings.openai_chat_model,
        "evaluator_embedding": pipeline.embedding_provider.model,
        "prompt_version": settings.prompt_version,
        "reason": None
        if settings.openai_api_key
        else "OPENAI_API_KEY is required for the Official RAGAS judge.",
        "official_metrics": [
            "faithfulness",
            "answer_relevancy",
            "context_precision",
            "context_recall",
        ],
        "local_metrics": [
            "answer_token_f1",
            "source_hit_rate",
            "page_hit_rate",
            "refusal_accuracy",
            "faithfulness_proxy",
            "answer_relevancy_proxy",
            "context_precision_proxy",
            "context_recall_proxy",
            "average_latency_ms",
        ],
        "limitations": "Metrics ending in _proxy are internal diagnostics and are not Official RAGAS.",
    }


@app.get("/api/finetuning/status")
def finetuning_status() -> dict[str, Any]:
    settings = load_settings()
    train_path = settings.finetuning_dir / "train.jsonl"
    validation_path = settings.finetuning_dir / "validation.jsonl"
    packages = ["torch", "transformers", "datasets", "peft", "trl", "bitsandbytes"]
    config_path = ROOT / "experiments" / "lora_config.json"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    candidate_adapter = Path(config["output_dir"])
    if not candidate_adapter.is_absolute():
        candidate_adapter = (ROOT / candidate_adapter).resolve()
    candidate_manifest_path = candidate_adapter / "training_manifest.json"
    candidate_manifest = (
        json.loads(candidate_manifest_path.read_text(encoding="utf-8"))
        if candidate_manifest_path.exists()
        else None
    )
    dataset_summary_path = settings.finetuning_dir / "dataset_summary.json"
    return {
        "train_dataset": validate_jsonl(train_path),
        "validation_dataset": validate_jsonl(validation_path),
        "packages": {name: importlib.util.find_spec(name) is not None for name in packages},
        "config": config,
        "dataset_summary": (
            json.loads(dataset_summary_path.read_text(encoding="utf-8"))
            if dataset_summary_path.exists()
            else None
        ),
        "candidate_adapter": str(candidate_adapter),
        "candidate_quality_gate": (candidate_manifest or {}).get("quality_gate"),
        "candidate_metrics": (candidate_manifest or {}).get("evaluation_metrics"),
        "ready_for_dry_run": train_path.exists(),
        "training_command": "python experiments/train_lora.py --config experiments/lora_config.json",
    }


@app.post("/api/finetuning/prepare")
def prepare_finetuning(request: FineTuningPrepareRequest) -> dict[str, Any]:
    source_path = Path(request.source_csv)
    if not source_path.is_absolute():
        source_path = ROOT / source_path
    source_path = source_path.resolve()
    if not source_path.exists() or not source_path.is_file():
        raise HTTPException(status_code=404, detail="Không tìm thấy CSV nguồn.")

    settings = load_settings()
    ensure_data_dirs(settings)
    try:
        return prepare_dataset(
            source_csv=source_path,
            output_dir=settings.finetuning_dir,
            validation_ratio=request.validation_ratio,
            seed=request.seed,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không thể chuẩn bị dataset: {exc}") from exc


def build_benchmark_runner(request: BenchmarkRequest) -> tuple[BenchmarkRunner, Path]:
    path = Path(request.test_set_path)
    if not path.is_absolute():
        path = ROOT / path
    path = path.resolve()
    if not path.exists() or not path.is_file():
        raise ValueError("Không tìm thấy test set CSV.")

    provider = request.generation_provider.strip().lower()
    if provider not in {"auto", "lora", "local", "extractive", "openai"}:
        raise ValueError("generation_provider không hợp lệ.")
    settings = replace(load_settings(), generation_provider=provider)
    benchmark_pipeline = RAGPipeline(settings, store, pipeline.embedding_provider)
    return BenchmarkRunner(benchmark_pipeline, store), path


def benchmark_quality_score(metrics: dict[str, Any]) -> float:
    keys = [
        "answer_token_f1",
        "source_hit_rate",
        "page_hit_rate",
        "refusal_accuracy",
        "answer_relevancy_proxy",
    ]
    values = [float(metrics.get(key, 0)) for key in keys]
    return round(sum(values) / len(values), 4)
