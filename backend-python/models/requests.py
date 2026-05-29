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

