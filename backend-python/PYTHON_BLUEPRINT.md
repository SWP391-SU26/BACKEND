# PYTHON AI ENGINE — CODE BLUEPRINT
# SU26SWP09 | Dùng file này để prompt Git AI / Claude Code sinh code
# Mỗi section là 1 file, có đủ skeleton + docstring + convention

---

## HƯỚNG DẪN DÙNG FILE NÀY

Paste toàn bộ file này vào Claude Code (VSCode) kèm lệnh:
> "Implement tất cả các file trong blueprint này theo đúng skeleton và convention đã mô tả.
>  Giữ đúng function signatures, type hints, error codes, và loguru logging."

Hoặc làm từng file:
> "Implement file `src/embeddings.py` theo blueprint section 4."

---

## CONVENTION CHUNG (áp dụng cho TẤT CẢ file)

```
- Async: tất cả I/O functions dùng async def + await
- Type hints: đầy đủ cho mọi function (params + return type)
- Logging: dùng loguru, KHÔNG dùng print() hay logging stdlib
- Error: raise HTTPException với detail={"code": "XXX-000", "message": "..."}
- Response format: {"data": ..., "error": None} hoặc {"data": None, "error": {...}}
- Naming: snake_case tất cả
- Docstring: ngắn gọn 1 dòng mô tả mục đích
```

---

## FILE 1: app/config.py

```python
"""
Load environment variables từ .env
Python AI Engine chỉ cần config cho AI layer — không có auth, không có Azure SQL
"""

from pydantic_settings import BaseSettings
from typing import Optional
from loguru import logger
from functools import lru_cache


class Settings(BaseSettings):
    # Qdrant Vector DB
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333

    # LLM
    openai_api_key: Optional[str] = None
    gemini_api_key: Optional[str] = None
    llm_model: str = "gpt-4o-mini"

    # Embedding
    embedding_model_default: str = "BAAI/bge-m3"

    # Fine-tuned model (HuggingFace Inference API)
    finetuned_model_endpoint: Optional[str] = None
    huggingface_api_key: Optional[str] = None

    # Server
    python_ai_port: int = 8001
    log_level: str = "INFO"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


@lru_cache
def get_settings() -> Settings:
    """Singleton settings — cache sau lần đầu load"""
    return Settings()


def log_config_status() -> None:
    """Log trạng thái config khi khởi động"""
    s = get_settings()
    logger.info("=== AI Engine Config ===")
    logger.info(f"Qdrant: {s.qdrant_host}:{s.qdrant_port}")
    logger.info(f"LLM: {s.llm_model}")
    logger.info(f"Embedding default: {s.embedding_model_default}")
    logger.info(f"OpenAI key: {'set' if s.openai_api_key else 'NOT SET'}")
    logger.info(f"Gemini key: {'set' if s.gemini_api_key else 'NOT SET'}")
    logger.info(f"HuggingFace endpoint: {'set' if s.finetuned_model_endpoint else 'NOT SET'}")
    logger.info("========================")
```

---

## FILE 2: app/main.py

```python
"""
FastAPI entrypoint — AI Engine
Expose 5 internal endpoints cho Java gọi
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from loguru import logger
from app.config import log_config_status

# Import routers (implement sau)
from api.index_document import router as index_router
from api.chat import router as chat_router
from api.chat_finetuned import router as finetuned_router
from api.evaluate import router as evaluate_router
from api.benchmark import router as benchmark_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup và shutdown events"""
    logger.info("Starting AI Engine...")
    log_config_status()
    # TODO: kiểm tra kết nối Qdrant khi startup
    yield
    logger.info("Shutting down AI Engine...")


app = FastAPI(
    title="SU26SWP09 — AI Engine",
    version="1.0.0",
    description="Internal AI service: RAG + Fine-tuned pipeline. Chỉ Java gọi, FE không gọi trực tiếp.",
    lifespan=lifespan
)

# CORS — chỉ cho phép Java BE gọi
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],  # Java port
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

# Đăng ký 5 routers
app.include_router(index_router, prefix="/ai", tags=["Document Indexing"])
app.include_router(chat_router, prefix="/ai", tags=["RAG Chat"])
app.include_router(finetuned_router, prefix="/ai", tags=["Fine-tuned Chat"])
app.include_router(evaluate_router, prefix="/ai", tags=["Evaluator"])
app.include_router(benchmark_router, prefix="/ai", tags=["Benchmark"])


@app.get("/health")
async def health():
    """Health check — Java gọi để kiểm tra Python còn sống không"""
    return {"status": "ok", "service": "ai-engine", "version": "1.0.0"}
```

---

## FILE 3: models/requests.py

```python
"""Pydantic request schemas — khớp với API contract Java gửi sang"""

from pydantic import BaseModel, Field
from typing import Optional
from enum import Enum


class EmbeddingModel(str, Enum):
    BGE_M3 = "bge-m3"
    E5_BASE = "multilingual-e5-base"
    OPENAI = "text-embedding-3-small"
    PHOBERT = "phobert"


class ChunkItem(BaseModel):
    chunk_index: int
    content: str
    source_page: int
    token_count: int


class IndexDocumentRequest(BaseModel):
    """POST /ai/index-document"""
    document_id: str
    chunks: list[ChunkItem]
    embedding_model: EmbeddingModel = EmbeddingModel.BGE_M3
    collection_name: str  # ví dụ: e4_bgem3_500_k3


class ConversationTurn(BaseModel):
    role: str  # "user" | "assistant"
    content: str


class ChatRequest(BaseModel):
    """POST /ai/chat"""
    question: str
    collection_name: str
    embedding_model: EmbeddingModel = EmbeddingModel.BGE_M3
    top_k: int = Field(default=5, ge=1, le=10)
    similarity_threshold: float = Field(default=0.72, ge=0.0, le=1.0)
    conversation_history: list[ConversationTurn] = []


class ChatFinetunedRequest(BaseModel):
    """POST /ai/chat-finetuned"""
    question: str
    conversation_history: list[ConversationTurn] = []


class EvaluateRequest(BaseModel):
    """POST /ai/evaluate"""
    question: str
    rag_answer: str
    finetuned_answer: str
    rag_score: float
    finetuned_score: float


class BenchmarkConfig(BaseModel):
    collection_name: str
    embedding_model: EmbeddingModel
    top_k: int = 5
    chunk_size: int = 500
    similarity_threshold: float = 0.72


class QuestionItem(BaseModel):
    question_id: str
    question: str
    ground_truth: str


class BenchmarkRequest(BaseModel):
    """POST /ai/benchmark"""
    experiment_id: str
    config: BenchmarkConfig
    questions: list[QuestionItem]
```

---

## FILE 4: models/responses.py

```python
"""Pydantic response schemas — khớp với API contract trả về Java"""

from pydantic import BaseModel
from typing import Optional, Any


class CitationRef(BaseModel):
    chunk_id: str
    document_id: str
    source_page: int
    excerpt: str
    similarity_score: float


class IndexDocumentResponse(BaseModel):
    collection_name: str
    point_ids: list[str]
    total_chunks: int
    status: str = "success"


class ChatResponse(BaseModel):
    rag_answer: str
    citations: list[CitationRef]
    rag_score: float
    is_out_of_scope: bool = False
    tokens_used: int
    latency_ms: int


class ChatFinetunedResponse(BaseModel):
    finetuned_answer: str
    finetuned_score: float
    latency_ms: int


class EvaluateResponse(BaseModel):
    winner: str  # "rag" | "finetuned" | "tie"
    scores: dict[str, float]
    reason: str


class RAGASResult(BaseModel):
    question_id: str
    generated_answer: str
    faithfulness: Optional[float] = None
    answer_relevancy: Optional[float] = None
    context_precision: Optional[float] = None
    context_recall: Optional[float] = None
    latency_ms: int


class BenchmarkSummary(BaseModel):
    avg_faithfulness: float
    avg_answer_relevancy: float
    avg_context_precision: float
    avg_context_recall: float


class BenchmarkResponse(BaseModel):
    experiment_id: str
    results: list[RAGASResult]
    summary: BenchmarkSummary


class APIResponse(BaseModel):
    """Wrapper chung cho mọi response"""
    data: Optional[Any] = None
    error: Optional[dict] = None
```

---

## FILE 5: src/embeddings.py

```python
"""
Wrapper cho 4 embedding models
Tất cả return normalized L2 vectors
Cache model instance theo model_name (singleton per process)
"""

from typing import Callable
from loguru import logger
import numpy as np


# Model cache — load 1 lần, tái dụng mãi
_model_cache: dict = {}


def _get_bge_m3(texts: list[str]) -> list[list[float]]:
    """BAAI/bge-m3 — recommended primary model"""
    from sentence_transformers import SentenceTransformer
    if "bge-m3" not in _model_cache:
        logger.info("Loading BAAI/bge-m3...")
        _model_cache["bge-m3"] = SentenceTransformer("BAAI/bge-m3")
    model = _model_cache["bge-m3"]
    vectors = model.encode(texts, batch_size=32, normalize_embeddings=True)
    return vectors.tolist()


def _get_e5_base(texts: list[str], is_query: bool = False) -> list[list[float]]:
    """multilingual-e5-base — cần prefix 'query:' hoặc 'passage:'"""
    from sentence_transformers import SentenceTransformer
    if "e5-base" not in _model_cache:
        logger.info("Loading multilingual-e5-base...")
        _model_cache["e5-base"] = SentenceTransformer("intfloat/multilingual-e5-base")
    model = _model_cache["e5-base"]
    prefix = "query: " if is_query else "passage: "
    prefixed = [prefix + t for t in texts]
    vectors = model.encode(prefixed, normalize_embeddings=True)
    return vectors.tolist()


async def _get_openai(texts: list[str]) -> list[list[float]]:
    """text-embedding-3-small — OpenAI API, retry 3 lần"""
    from openai import AsyncOpenAI
    from app.config import get_settings
    import asyncio
    client = AsyncOpenAI(api_key=get_settings().openai_api_key)
    for attempt in range(3):
        try:
            response = await client.embeddings.create(
                model="text-embedding-3-small",
                input=texts
            )
            vectors = [item.embedding for item in response.data]
            # Normalize
            norms = [np.linalg.norm(v) for v in vectors]
            return [[x / n for x, n in zip(v, [norm]*len(v))] for v, norm in zip(vectors, norms)]
        except Exception as e:
            if attempt == 2:
                raise
            logger.warning(f"OpenAI embedding retry {attempt+1}: {e}")
            await asyncio.sleep(2 ** attempt)


def _get_phobert(texts: list[str]) -> list[list[float]]:
    """PhoBERT — cần mean pooling thủ công"""
    import torch
    from transformers import AutoTokenizer, AutoModel
    if "phobert" not in _model_cache:
        logger.info("Loading PhoBERT...")
        tokenizer = AutoTokenizer.from_pretrained("vinai/phobert-base")
        model = AutoModel.from_pretrained("vinai/phobert-base")
        model.eval()
        _model_cache["phobert"] = (tokenizer, model)
    tokenizer, model = _model_cache["phobert"]

    encoded = tokenizer(texts, padding=True, truncation=True, max_length=256, return_tensors="pt")
    with torch.no_grad():
        output = model(**encoded)
    # Mean pooling
    attention_mask = encoded["attention_mask"]
    token_embeddings = output.last_hidden_state
    input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    vectors = torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(input_mask_expanded.sum(1), min=1e-9)
    # Normalize L2
    vectors = torch.nn.functional.normalize(vectors, p=2, dim=1)
    return vectors.tolist()


async def embed_texts(texts: list[str], model_name: str, is_query: bool = False) -> list[list[float]]:
    """
    Entry point chính — embed list of texts
    model_name: "bge-m3" | "multilingual-e5-base" | "text-embedding-3-small" | "phobert"
    is_query: True nếu embed câu hỏi (ảnh hưởng e5 prefix)
    """
    logger.debug(f"Embedding {len(texts)} texts with {model_name}")
    if model_name == "bge-m3":
        return _get_bge_m3(texts)
    elif model_name == "multilingual-e5-base":
        return _get_e5_base(texts, is_query=is_query)
    elif model_name == "text-embedding-3-small":
        return await _get_openai(texts)
    elif model_name == "phobert":
        return _get_phobert(texts)
    else:
        raise ValueError(f"Unknown embedding model: {model_name}")


async def embed_query(query: str, model_name: str) -> list[float]:
    """Embed 1 câu hỏi — shorthand cho single query"""
    vectors = await embed_texts([query], model_name, is_query=True)
    return vectors[0]
```

---

## FILE 6: src/vector_store.py

```python
"""
Qdrant client wrapper
Hỗ trợ: upsert chunks, search by vector, delete by document_id
"""

from qdrant_client import AsyncQdrantClient
from qdrant_client.models import (
    VectorParams, Distance, PointStruct,
    Filter, FieldCondition, MatchValue
)
from loguru import logger
from app.config import get_settings
import uuid


def _get_client() -> AsyncQdrantClient:
    """Singleton Qdrant client"""
    s = get_settings()
    return AsyncQdrantClient(host=s.qdrant_host, port=s.qdrant_port)


async def ensure_collection(collection_name: str, vector_size: int) -> None:
    """Tạo collection nếu chưa tồn tại"""
    client = _get_client()
    collections = await client.get_collections()
    names = [c.name for c in collections.collections]
    if collection_name not in names:
        await client.create_collection(
            collection_name=collection_name,
            vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE)
        )
        logger.info(f"Created Qdrant collection: {collection_name}")


async def upsert_chunks(
    collection_name: str,
    chunks: list[dict],
    vectors: list[list[float]],
    document_id: str
) -> list[str]:
    """
    Lưu chunks + vectors vào Qdrant
    chunks: list of {chunk_index, content, source_page, token_count}
    Trả về list point_ids
    """
    client = _get_client()
    await ensure_collection(collection_name, len(vectors[0]))

    points = []
    point_ids = []
    for chunk, vector in zip(chunks, vectors):
        point_id = str(uuid.uuid4())
        point_ids.append(point_id)
        points.append(PointStruct(
            id=point_id,
            vector=vector,
            payload={
                "document_id": document_id,
                "chunk_index": chunk["chunk_index"],
                "content": chunk["content"],
                "source_page": chunk["source_page"],
                "token_count": chunk["token_count"]
            }
        ))

    await client.upsert(collection_name=collection_name, points=points)
    logger.info(f"Upserted {len(points)} chunks to {collection_name}")
    return point_ids


async def search_similar(
    collection_name: str,
    query_vector: list[float],
    top_k: int = 5,
    score_threshold: float = 0.72
) -> list[dict]:
    """
    Tìm top_k chunks giống query_vector nhất
    Trả về list {chunk_id, content, document_id, source_page, similarity_score}
    """
    client = _get_client()
    results = await client.search(
        collection_name=collection_name,
        query_vector=query_vector,
        limit=top_k,
        score_threshold=score_threshold,
        with_payload=True
    )
    return [
        {
            "chunk_id": str(r.id),
            "content": r.payload["content"],
            "document_id": r.payload["document_id"],
            "source_page": r.payload["source_page"],
            "similarity_score": r.score
        }
        for r in results
    ]


async def delete_by_document(collection_name: str, document_id: str) -> int:
    """Xóa tất cả chunks của 1 document khỏi collection"""
    client = _get_client()
    result = await client.delete(
        collection_name=collection_name,
        points_selector=Filter(
            must=[FieldCondition(key="document_id", match=MatchValue(value=document_id))]
        )
    )
    logger.info(f"Deleted chunks of document {document_id} from {collection_name}")
    return result.operation_id
```

---

## FILE 7: src/rag_pipeline.py

```python
"""
RAG Pipeline: embed query → search Qdrant → build prompt → call LLM → parse citations
"""

from loguru import logger
from fastapi import HTTPException
from src.embeddings import embed_query
from src.vector_store import search_similar
from app.config import get_settings
import time


OUT_OF_SCOPE_MSG = "Câu hỏi này nằm ngoài phạm vi tài liệu môn học. Vui lòng hỏi các nội dung liên quan đến tài liệu đã được cung cấp."

SYSTEM_PROMPT = """Bạn là trợ lý học tập cho sinh viên FPT University.
Chỉ trả lời dựa trên các đoạn tài liệu được cung cấp trong context bên dưới.
Nếu thông tin không có trong tài liệu, trả lời đúng 1 câu: "Câu hỏi này nằm ngoài phạm vi tài liệu môn học."
Trích dẫn nguồn theo format [1], [2] tương ứng với thứ tự chunk trong context.
Trả lời bằng tiếng Việt, rõ ràng và chính xác."""


def _build_context(chunks: list[dict]) -> str:
    """Ghép chunks thành context string cho prompt"""
    parts = []
    for i, chunk in enumerate(chunks, 1):
        parts.append(f"[{i}] (Trang {chunk['source_page']}): {chunk['content']}")
    return "\n\n".join(parts)


async def _call_llm(prompt: str, system: str, conversation_history: list[dict]) -> tuple[str, int]:
    """
    Gọi LLM API (OpenAI hoặc Gemini)
    Trả về (answer_text, tokens_used)
    """
    s = get_settings()
    messages = []

    # Thêm lịch sử hội thoại (tối đa 6 turns gần nhất)
    for turn in conversation_history[-6:]:
        messages.append({"role": turn["role"], "content": turn["content"]})
    messages.append({"role": "user", "content": prompt})

    if s.openai_api_key:
        from openai import AsyncOpenAI
        client = AsyncOpenAI(api_key=s.openai_api_key)
        response = await client.chat.completions.create(
            model=s.llm_model,
            messages=[{"role": "system", "content": system}] + messages,
            temperature=0.1,
            timeout=30
        )
        return response.choices[0].message.content, response.usage.total_tokens

    elif s.gemini_api_key:
        import google.generativeai as genai
        genai.configure(api_key=s.gemini_api_key)
        model = genai.GenerativeModel("gemini-1.5-flash", system_instruction=system)
        response = model.generate_content(prompt)
        return response.text, 0  # Gemini không trả token count dễ dàng

    else:
        raise HTTPException(status_code=500, detail={"code": "RAG-003", "message": "No LLM API key configured"})


async def run_rag(
    question: str,
    collection_name: str,
    embedding_model: str,
    top_k: int,
    similarity_threshold: float,
    conversation_history: list[dict]
) -> dict:
    """
    Full RAG pipeline
    Trả về dict khớp với ChatResponse schema
    """
    start_time = time.time()

    # 1. Embed query
    query_vector = await embed_query(question, embedding_model)

    # 2. Search Qdrant
    chunks = await search_similar(collection_name, query_vector, top_k, similarity_threshold)

    # 3. Out-of-scope check
    if not chunks or chunks[0]["similarity_score"] < 0.5:
        logger.info(f"Out of scope: top score={chunks[0]['similarity_score'] if chunks else 0}")
        return {
            "rag_answer": OUT_OF_SCOPE_MSG,
            "citations": [],
            "rag_score": 0.0,
            "is_out_of_scope": True,
            "tokens_used": 0,
            "latency_ms": int((time.time() - start_time) * 1000)
        }

    # 4. Build prompt
    context = _build_context(chunks)
    prompt = f"Context tài liệu:\n{context}\n\nCâu hỏi: {question}"

    # 5. Call LLM
    answer, tokens = await _call_llm(prompt, SYSTEM_PROMPT, conversation_history)

    # 6. Build citations
    citations = [
        {
            "chunk_id": c["chunk_id"],
            "document_id": c["document_id"],
            "source_page": c["source_page"],
            "excerpt": c["content"][:150] + "..." if len(c["content"]) > 150 else c["content"],
            "similarity_score": c["similarity_score"]
        }
        for c in chunks
    ]

    latency = int((time.time() - start_time) * 1000)
    avg_score = sum(c["similarity_score"] for c in chunks) / len(chunks)

    logger.info(f"RAG done: {len(chunks)} chunks, score={avg_score:.2f}, latency={latency}ms")

    return {
        "rag_answer": answer,
        "citations": citations,
        "rag_score": avg_score,
        "is_out_of_scope": False,
        "tokens_used": tokens,
        "latency_ms": latency
    }
```

---

## FILE 8: src/finetuned_pipeline.py

```python
"""
Fine-tuned model inference qua HuggingFace Inference API
"""

import httpx
import time
from loguru import logger
from fastapi import HTTPException
from app.config import get_settings


async def run_finetuned(question: str, conversation_history: list[dict]) -> dict:
    """
    Gọi HuggingFace Inference API
    Trả về dict khớp với ChatFinetunedResponse schema
    """
    s = get_settings()
    start_time = time.time()

    if not s.finetuned_model_endpoint or not s.huggingface_api_key:
        raise HTTPException(
            status_code=503,
            detail={"code": "FT-001", "message": "Fine-tuned model not configured"}
        )

    # Build input text
    history_text = ""
    for turn in conversation_history[-4:]:
        role = "Sinh viên" if turn["role"] == "user" else "Trợ lý"
        history_text += f"{role}: {turn['content']}\n"
    input_text = f"{history_text}Sinh viên: {question}\nTrợ lý:"

    headers = {"Authorization": f"Bearer {s.huggingface_api_key}"}
    payload = {
        "inputs": input_text,
        "parameters": {
            "max_new_tokens": 512,
            "temperature": 0.1,
            "do_sample": False
        }
    }

    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(s.finetuned_model_endpoint, json=payload, headers=headers)
            response.raise_for_status()
            result = response.json()

        # HuggingFace trả về list hoặc dict tùy model
        if isinstance(result, list):
            answer = result[0].get("generated_text", "").replace(input_text, "").strip()
        else:
            answer = result.get("generated_text", "").replace(input_text, "").strip()

        latency = int((time.time() - start_time) * 1000)
        logger.info(f"Fine-tuned done: latency={latency}ms")

        # Score đơn giản dựa trên độ dài answer (placeholder — replace bằng proper scoring)
        score = min(len(answer) / 200, 1.0) if answer else 0.0

        return {
            "finetuned_answer": answer,
            "finetuned_score": score,
            "latency_ms": latency
        }

    except httpx.TimeoutException:
        raise HTTPException(status_code=504, detail={"code": "FT-002", "message": "Fine-tuned model timeout"})
    except Exception as e:
        logger.error(f"Fine-tuned error: {e}")
        raise HTTPException(status_code=502, detail={"code": "FT-001", "message": str(e)})
```

---

## FILE 9: src/evaluator.py

```python
"""
So sánh RAG answer vs Fine-tuned answer, chọn winner
Dùng LLM-as-judge để chấm điểm khách quan
"""

import re
from loguru import logger
from fastapi import HTTPException
from app.config import get_settings


JUDGE_PROMPT = """Bạn là giám khảo đánh giá chất lượng câu trả lời cho sinh viên.

Câu hỏi: {question}

Câu trả lời A (RAG): {rag_answer}

Câu trả lời B (Fine-tuned): {finetuned_answer}

Hãy chấm điểm từ 0.0 đến 1.0 cho mỗi câu trả lời dựa trên:
- Độ chính xác và đầy đủ
- Rõ ràng, dễ hiểu
- Phù hợp với câu hỏi

Trả lời ĐÚNG FORMAT sau (không thêm gì khác):
SCORE_A: <số thập phân>
SCORE_B: <số thập phân>
REASON: <1 câu giải thích ngắn>"""


def _parse_judge_response(response: str) -> tuple[float, float, str]:
    """Parse response từ LLM judge"""
    score_a = float(re.search(r"SCORE_A:\s*([\d.]+)", response).group(1))
    score_b = float(re.search(r"SCORE_B:\s*([\d.]+)", response).group(1))
    reason = re.search(r"REASON:\s*(.+)", response).group(1).strip()
    return score_a, score_b, reason


async def evaluate_answers(
    question: str,
    rag_answer: str,
    finetuned_answer: str,
    rag_score: float,
    finetuned_score: float
) -> dict:
    """
    So sánh 2 answer, chọn winner
    Trả về dict khớp với EvaluateResponse schema
    """
    s = get_settings()

    # Nếu 1 trong 2 là out-of-scope, winner rõ ràng
    out_of_scope_msg = "nằm ngoài phạm vi"
    if out_of_scope_msg in rag_answer.lower() and out_of_scope_msg not in finetuned_answer.lower():
        return {"winner": "finetuned", "scores": {"rag": 0.0, "finetuned": finetuned_score}, "reason": "RAG out of scope"}
    if out_of_scope_msg in finetuned_answer.lower() and out_of_scope_msg not in rag_answer.lower():
        return {"winner": "rag", "scores": {"rag": rag_score, "finetuned": 0.0}, "reason": "Fine-tuned out of scope"}

    # Dùng LLM-as-judge
    prompt = JUDGE_PROMPT.format(
        question=question,
        rag_answer=rag_answer,
        finetuned_answer=finetuned_answer
    )

    try:
        if s.openai_api_key:
            from openai import AsyncOpenAI
            client = AsyncOpenAI(api_key=s.openai_api_key)
            response = await client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.0
            )
            judge_text = response.choices[0].message.content
        else:
            # Fallback: so sánh bằng score gốc từ retrieval
            judge_text = f"SCORE_A: {rag_score}\nSCORE_B: {finetuned_score}\nREASON: Based on retrieval scores"

        score_a, score_b, reason = _parse_judge_response(judge_text)

    except Exception as e:
        logger.warning(f"LLM judge failed, fallback to scores: {e}")
        score_a, score_b, reason = rag_score, finetuned_score, "Fallback to retrieval scores"

    # Quyết định winner (tie nếu chênh < 0.05)
    diff = abs(score_a - score_b)
    if diff < 0.05:
        winner = "tie"
    elif score_a > score_b:
        winner = "rag"
    else:
        winner = "finetuned"

    logger.info(f"Evaluator: winner={winner}, rag={score_a:.2f}, ft={score_b:.2f}")
    return {
        "winner": winner,
        "scores": {"rag": score_a, "finetuned": score_b},
        "reason": reason
    }
```

---

## FILE 10: api/index_document.py

```python
"""POST /ai/index-document — Java gọi sau khi upload file xong"""

from fastapi import APIRouter, HTTPException
from loguru import logger
from models.requests import IndexDocumentRequest
from models.responses import IndexDocumentResponse
from src.embeddings import embed_texts
from src.vector_store import upsert_chunks

router = APIRouter()


@router.post("/index-document", response_model=IndexDocumentResponse)
async def index_document(request: IndexDocumentRequest):
    """Nhận chunks từ Java, embed và lưu vào Qdrant"""
    logger.info(f"Indexing document {request.document_id}: {len(request.chunks)} chunks, model={request.embedding_model}")

    try:
        texts = [chunk.content for chunk in request.chunks]
        vectors = await embed_texts(texts, request.embedding_model.value)

        chunks_dict = [chunk.model_dump() for chunk in request.chunks]
        point_ids = await upsert_chunks(
            collection_name=request.collection_name,
            chunks=chunks_dict,
            vectors=vectors,
            document_id=request.document_id
        )

        return IndexDocumentResponse(
            collection_name=request.collection_name,
            point_ids=point_ids,
            total_chunks=len(point_ids)
        )

    except Exception as e:
        logger.error(f"Index failed for {request.document_id}: {e}")
        raise HTTPException(status_code=500, detail={"code": "IDX-002", "message": str(e)})
```

---

## FILE 11: api/chat.py

```python
"""POST /ai/chat — RAG pipeline"""

from fastapi import APIRouter, HTTPException
from loguru import logger
from models.requests import ChatRequest
from models.responses import ChatResponse
from src.rag_pipeline import run_rag

router = APIRouter()


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """Chạy RAG pipeline, trả answer + citations"""
    logger.info(f"Chat request: '{request.question[:50]}...' collection={request.collection_name}")

    try:
        result = await run_rag(
            question=request.question,
            collection_name=request.collection_name,
            embedding_model=request.embedding_model.value,
            top_k=request.top_k,
            similarity_threshold=request.similarity_threshold,
            conversation_history=[t.model_dump() for t in request.conversation_history]
        )
        return ChatResponse(**result)

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"RAG pipeline error: {e}")
        raise HTTPException(status_code=500, detail={"code": "RAG-003", "message": str(e)})
```

---

## FILE 12: api/chat_finetuned.py

```python
"""POST /ai/chat-finetuned — Fine-tuned model inference"""

from fastapi import APIRouter, HTTPException
from loguru import logger
from models.requests import ChatFinetunedRequest
from models.responses import ChatFinetunedResponse
from src.finetuned_pipeline import run_finetuned

router = APIRouter()


@router.post("/chat-finetuned", response_model=ChatFinetunedResponse)
async def chat_finetuned(request: ChatFinetunedRequest):
    """Gọi fine-tuned model trên HuggingFace"""
    logger.info(f"Fine-tuned request: '{request.question[:50]}...'")

    result = await run_finetuned(
        question=request.question,
        conversation_history=[t.model_dump() for t in request.conversation_history]
    )
    return ChatFinetunedResponse(**result)
```

---

## FILE 13: api/evaluate.py

```python
"""POST /ai/evaluate — So sánh 2 answers, chọn winner"""

from fastapi import APIRouter
from loguru import logger
from models.requests import EvaluateRequest
from models.responses import EvaluateResponse
from src.evaluator import evaluate_answers

router = APIRouter()


@router.post("/evaluate", response_model=EvaluateResponse)
async def evaluate(request: EvaluateRequest):
    """LLM-as-judge: chọn winner giữa RAG và fine-tuned"""
    logger.info(f"Evaluate: question='{request.question[:50]}...'")

    result = await evaluate_answers(
        question=request.question,
        rag_answer=request.rag_answer,
        finetuned_answer=request.finetuned_answer,
        rag_score=request.rag_score,
        finetuned_score=request.finetuned_score
    )
    return EvaluateResponse(**result)
```

---

## FILE 14: api/benchmark.py

```python
"""POST /ai/benchmark — Chạy RAGAS evaluation cho experiment"""

from fastapi import APIRouter, HTTPException
from loguru import logger
from models.requests import BenchmarkRequest
from models.responses import BenchmarkResponse, RAGASResult, BenchmarkSummary
from src.rag_pipeline import run_rag
import time

router = APIRouter()


@router.post("/benchmark", response_model=BenchmarkResponse)
async def benchmark(request: BenchmarkRequest):
    """
    Chạy toàn bộ test set qua RAG pipeline
    Tính RAGAS metrics cho từng câu
    """
    logger.info(f"Benchmark experiment {request.experiment_id}: {len(request.questions)} questions")

    results = []
    generated_answers = []
    retrieved_contexts_list = []

    # Bước 1: Chạy RAG cho từng câu
    for q in request.questions:
        try:
            rag_result = await run_rag(
                question=q.question,
                collection_name=request.config.collection_name,
                embedding_model=request.config.embedding_model.value,
                top_k=request.config.top_k,
                similarity_threshold=request.config.similarity_threshold,
                conversation_history=[]
            )
            generated_answers.append(rag_result["rag_answer"])
            retrieved_contexts_list.append([c["content"] for c in rag_result["citations"]])
            results.append({
                "question_id": q.question_id,
                "question": q.question,
                "answer": rag_result["rag_answer"],
                "contexts": [c["content"] for c in rag_result["citations"]],
                "ground_truth": q.ground_truth,
                "latency_ms": rag_result["latency_ms"]
            })
        except Exception as e:
            logger.error(f"Error on question {q.question_id}: {e}")
            results.append({
                "question_id": q.question_id,
                "question": q.question,
                "answer": "",
                "contexts": [],
                "ground_truth": q.ground_truth,
                "latency_ms": 0
            })

    # Bước 2: Tính RAGAS metrics
    ragas_results = await _compute_ragas(results)

    # Bước 3: Tính summary
    summary = _compute_summary(ragas_results)

    return BenchmarkResponse(
        experiment_id=request.experiment_id,
        results=ragas_results,
        summary=summary
    )


async def _compute_ragas(results: list[dict]) -> list[RAGASResult]:
    """Tính RAGAS metrics — wrapper để dễ mock trong test"""
    try:
        from ragas import evaluate
        from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall
        from datasets import Dataset

        valid = [r for r in results if r["answer"] and r["contexts"]]
        if not valid:
            raise ValueError("No valid results to evaluate")

        dataset = Dataset.from_list([
            {
                "question": r["question"],
                "answer": r["answer"],
                "contexts": r["contexts"],
                "ground_truth": r["ground_truth"]
            }
            for r in valid
        ])

        scores = evaluate(dataset, metrics=[faithfulness, answer_relevancy, context_precision, context_recall])
        scores_df = scores.to_pandas()

        ragas_list = []
        valid_idx = 0
        for r in results:
            if r["answer"] and r["contexts"]:
                row = scores_df.iloc[valid_idx]
                ragas_list.append(RAGASResult(
                    question_id=r["question_id"],
                    generated_answer=r["answer"],
                    faithfulness=float(row.get("faithfulness", 0)),
                    answer_relevancy=float(row.get("answer_relevancy", 0)),
                    context_precision=float(row.get("context_precision", 0)),
                    context_recall=float(row.get("context_recall", 0)),
                    latency_ms=r["latency_ms"]
                ))
                valid_idx += 1
            else:
                ragas_list.append(RAGASResult(
                    question_id=r["question_id"],
                    generated_answer="",
                    latency_ms=r["latency_ms"]
                ))
        return ragas_list

    except Exception as e:
        logger.error(f"RAGAS evaluation failed: {e}")
        raise HTTPException(status_code=500, detail={"code": "BM-002", "message": str(e)})


def _compute_summary(results: list[RAGASResult]) -> BenchmarkSummary:
    """Tính average metrics"""
    valid = [r for r in results if r.faithfulness is not None]
    if not valid:
        return BenchmarkSummary(avg_faithfulness=0, avg_answer_relevancy=0,
                                avg_context_precision=0, avg_context_recall=0)
    n = len(valid)
    return BenchmarkSummary(
        avg_faithfulness=sum(r.faithfulness for r in valid) / n,
        avg_answer_relevancy=sum(r.answer_relevancy for r in valid) / n,
        avg_context_precision=sum(r.context_precision for r in valid) / n,
        avg_context_recall=sum(r.context_recall for r in valid) / n
    )
```

---

## FILE 15: .env.example

```env
# Qdrant Vector DB (local dev: localhost, production: GCP VM IP)
QDRANT_HOST=localhost
QDRANT_PORT=6333

# LLM — điền 1 trong 2
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=

# Chọn model
LLM_MODEL=gpt-4o-mini

# Embedding default
EMBEDDING_MODEL_DEFAULT=bge-m3

# Fine-tuned model (điền sau tuần 3 khi push model lên HuggingFace)
FINETUNED_MODEL_ENDPOINT=https://api-inference.huggingface.co/models/your-org/your-model
HUGGINGFACE_API_KEY=hf_...

# Server
PYTHON_AI_PORT=8001
LOG_LEVEL=INFO
```

---

## FILE 16: .gitignore

```
.venv/
__pycache__/
*.pyc
.env
*.egg-info/
.pytest_cache/
data/raw/
data/processed/
notebooks/.ipynb_checkpoints/
```

---

## LỆNH CHẠY PROJECT

```bash
# Kích hoạt venv
.venv\Scripts\activate

# Chạy FastAPI (hot reload)
uvicorn app.main:app --reload --port 8001

# Test health check
curl http://localhost:8001/health

# Xem Swagger docs
# Mở browser: http://localhost:8001/docs
```
