"""
Response schemas for FastAPI AI Engine
Used for output validation and documentation
"""

from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from enum import Enum


class StatusEnum(str, Enum):
    """Status of response"""
    SUCCESS = "success"
    ERROR = "error"
    LOW_CONFIDENCE = "low_confidence"


class WinnerEnum(str, Enum):
    """Winner of comparison between RAG and fine-tuned"""
    RAG = "rag"
    FINETUNED = "finetuned"
    TIE = "tie"


class IndexDocumentResponse(BaseModel):
    """Response from document indexing endpoint"""

    status: StatusEnum = Field(
        ...,
        description="Operation status",
        example="success"
    )
    collection_name: str = Field(
        ...,
        description="Qdrant collection name where document was stored",
        example="e1_e5base_300_k3"
    )
    point_ids: List[str] = Field(
        ...,
        description="Qdrant point IDs of indexed chunks",
        example=["point_001", "point_002"]
    )
    message: Optional[str] = Field(
        default=None,
        description="Additional message or error description",
        example="Document indexed successfully"
    )


class Citation(BaseModel):
    """Citation reference from source document"""

    document_id: str = Field(..., example="doc_123")
    chunk_index: int = Field(..., example=0)
    relevance_score: float = Field(..., example=0.92)
    text_preview: str = Field(..., example="This is the relevant text...")


class ChatResponse(BaseModel):
    """Response from RAG chat endpoint"""

    status: StatusEnum = Field(..., example="success")
    rag_answer: str = Field(
        ...,
        description="Answer from RAG pipeline",
        example="Giới hạn hàm số là..."
    )
    rag_score: float = Field(
        ...,
        description="Confidence score (0-100)",
        ge=0,
        le=100,
        example=85.5
    )
    citations: List[Citation] = Field(
        ...,
        description="Source references",
        example=[]
    )
    message: Optional[str] = Field(default=None, example="Answer retrieved")


class ChatFinetuneResponse(BaseModel):
    """Response from fine-tuned model chat endpoint"""

    status: StatusEnum = Field(..., example="success")
    finetuned_answer: str = Field(
        ...,
        description="Answer from fine-tuned model",
        example="Python là ngôn ngữ lập trình..."
    )
    finetuned_score: float = Field(
        ...,
        description="Confidence score (0-100)",
        ge=0,
        le=100,
        example=88.0
    )
    message: Optional[str] = Field(default=None)


class ComparisonScores(BaseModel):
    """Scores from comparing two answers"""

    rag: float = Field(..., ge=0, le=100, example=85)
    finetuned: float = Field(..., ge=0, le=100, example=78)
    semantic_similarity: Optional[float] = Field(default=None, ge=0, le=1, example=0.92)


class EvaluateResponse(BaseModel):
    """Response from evaluation endpoint"""

    status: StatusEnum = Field(..., example="success")
    winner: WinnerEnum = Field(
        ...,
        description="Which answer is better or tie",
        example="rag"
    )
    scores: ComparisonScores = Field(..., description="Detailed scores")
    recommendation: str = Field(
        ...,
        description="Human-readable recommendation for UI display",
        example="RAG answer is significantly better"
    )
    show_both: bool = Field(
        default=False,
        description="Whether to show both answers to user (in case of tie)",
        example=False
    )
    message: Optional[str] = Field(default=None)


class BenchmarkMetrics(BaseModel):
    """RAGAS metrics for single question"""

    question: str
    rag_answer: str
    finetuned_answer: str
    rag_score: float
    finetuned_score: float
    faithfulness: Optional[float] = None
    answer_relevancy: Optional[float] = None
    context_precision: Optional[float] = None
    context_recall: Optional[float] = None


class BenchmarkResponse(BaseModel):
    """Response from benchmark endpoint"""

    status: StatusEnum = Field(..., example="success")
    experiment_id: str = Field(..., example="exp_001_e5base_k3")
    results: List[BenchmarkMetrics] = Field(..., description="Metrics for each question")
    summary: Dict[str, Any] = Field(
        ...,
        description="Aggregated metrics",
        example={
            "avg_rag_score": 85.5,
            "avg_finetuned_score": 78.2,
            "rag_wins": 35,
            "finetuned_wins": 10,
            "ties": 5
        }
    )
    message: Optional[str] = Field(default=None)


class ErrorResponse(BaseModel):
    """Standard error response"""

    status: StatusEnum = Field(default="error")
    error_code: str = Field(..., example="QUESTION_TOO_SHORT")
    message: str = Field(..., example="Question must be at least 5 characters")
    details: Optional[Dict[str, Any]] = Field(default=None)
 
