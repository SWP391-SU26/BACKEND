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

