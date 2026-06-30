from __future__ import annotations

import shutil
import sys
import importlib.util
import json
from dataclasses import replace
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
from src.finetuning import prepare_dataset, validate_jsonl
from src.job_manager import BackgroundJobManager
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore
from src.text_utils import safe_filename


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

class GenerateRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    contexts: list[GenerateContext]

class GenerateSource(BaseModel):
    chunk_id: str
    document_id: str
    filename: str
    page: int | None = None
    location: str | None = None
    preview: str

class GenerateResponse(BaseModel):
    answer: str
    is_out_of_scope: bool
    sources: list[GenerateSource]

class ChatFinetunedRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)

class ChatFinetunedResponse(BaseModel):
    answer: str

class EvaluateRequest(BaseModel):
    question: str
    answer_rag: str
    answer_finetuned: str

class EvaluateResponse(BaseModel):
    evaluation: str

def build_pipeline() -> tuple[RAGPipeline, SQLiteStore]:
    settings = load_settings()
    ensure_data_dirs(settings)
    store = SQLiteStore(settings.db_path)
    embedding_provider = get_embedding_provider(settings)
    return RAGPipeline(settings, store, embedding_provider), store


pipeline, store = build_pipeline()
benchmark_runner = BenchmarkRunner(pipeline, store)
job_manager = BackgroundJobManager(max_workers=1)

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
    return {
        "embedding_model": pipeline.embedding_provider.model,
        "generation": pipeline.generation_status(),
    }


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
    from src.rag_pipeline import OUT_OF_SCOPE_MESSAGE, location_label
    from src.storage import RetrievedChunk
    
    if not request.contexts:
        return GenerateResponse(
            answer=OUT_OF_SCOPE_MESSAGE,
            is_out_of_scope=True,
            sources=[]
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
            score=1.0,
            semantic_score=1.0,
            lexical_score=1.0
        )
        contexts.append(chunk)
        sources_dict_list.append({
            "chunk_id": ctx.chunk_id,
            "document_id": ctx.document_id,
            "filename": ctx.filename,
            "page": ctx.page,
            "location": location_label(chunk),
            "preview": ctx.content[:280]
        })
        
    try:
        answer = pipeline._generate_answer(request.question, contexts, sources_dict_list)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Không thể tạo câu trả lời: {exc}") from exc
        
    return GenerateResponse(
        answer=answer,
        is_out_of_scope=(answer == OUT_OF_SCOPE_MESSAGE),
        sources=[GenerateSource(**s) for s in sources_dict_list]
    )

@app.post("/ai/chat-finetuned", response_model=ChatFinetunedResponse)
def chat_finetuned(request: ChatFinetunedRequest) -> ChatFinetunedResponse:
    try:
        answer = pipeline.generate_without_retrieval(request.question)
        return ChatFinetunedResponse(answer=answer)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Lỗi mô hình finetuned: {exc}") from exc

@app.post("/ai/evaluate", response_model=EvaluateResponse)
def evaluate_answers(request: EvaluateRequest) -> EvaluateResponse:
    # Không có API LLM trả phí, sử dụng một placeholder cơ bản
    eval_text = f"Đánh giá giả lập:\\nRAG: {request.answer_rag[:50]}...\\nFinetuned: {request.answer_finetuned[:50]}..."
    return EvaluateResponse(evaluation=eval_text)


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
    return {
        "official_ragas_enabled": False,
        "reason": (
            "Dự án không sử dụng API trả phí hoặc LLM judge đủ mạnh để chạy RAGAS chính thức."
        ),
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
        "limitations": (
            "Các metric proxy dựa trên token overlap; faithfulness_proxy có thể thấp khi "
            "context tiếng Anh nhưng câu trả lời được diễn đạt bằng tiếng Việt."
        ),
    }


@app.get("/api/finetuning/status")
def finetuning_status() -> dict[str, Any]:
    settings = load_settings()
    train_path = settings.finetuning_dir / "train.jsonl"
    validation_path = settings.finetuning_dir / "validation.jsonl"
    packages = ["torch", "transformers", "datasets", "peft", "trl", "bitsandbytes"]
    config_path = ROOT / "experiments" / "lora_config.json"
    return {
        "train_dataset": validate_jsonl(train_path),
        "validation_dataset": validate_jsonl(validation_path),
        "packages": {name: importlib.util.find_spec(name) is not None for name in packages},
        "config": json.loads(config_path.read_text(encoding="utf-8")),
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
