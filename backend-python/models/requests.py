"""
Request schemas for FastAPI AI Engine
Used for input validation and documentation
"""

from pydantic import BaseModel, Field
from typing import List, Optional


class IndexDocumentRequest(BaseModel):
    """
    Request to index a document into vector database
    Used by Java: POST /ai/index-document
    """

    document_id: str = Field(
        ..., 
        description="Unique identifier for the document",
        example="doc_123_calculus"
    )
    chunks: List[str] = Field(
        ...,
        description="List of text chunks from the document",
        example=["Calculus is the study of...", "Derivatives measure..."]
    )
    embedding_model: str = Field(
        default="BAAI/bge-m3",
        description="Embedding model to use for vectorization",
        example="BAAI/bge-m3"
    )
    metadata: Optional[dict] = Field(
        default=None,
        description="Additional metadata (course_name, chapter, etc.)",
        example={"course": "Calculus I", "chapter": "Limits"}
    )


class ChatRequest(BaseModel):
    """
    Request for RAG chat endpoint
    Used by Java: POST /ai/chat
    """

    question: str = Field(
        ...,
        description="User question",
        min_length=5,
        max_length=500,
        example="Định nghĩa giới hạn hàm số là gì?"
    )
    embedding_model: str = Field(
        default="BAAI/bge-m3",
        description="Embedding model for question encoding",
        example="BAAI/bge-m3"
    )
    top_k: int = Field(
        default=3,
        description="Number of top relevant chunks to retrieve",
        ge=1,
        le=10,
        example=3
    )
    chunking_strategy: str = Field(
        default="recursive",
        description="Text chunking strategy used during indexing",
        example="recursive"
    )


class ChatFinetuneRequest(BaseModel):
    """
    Request for fine-tuned model chat endpoint
    Used by Java: POST /ai/chat-finetuned
    """

    question: str = Field(
        ...,
        description="User question",
        min_length=5,
        max_length=500,
        example="Python là ngôn ngữ lập trình gì?"
    )


class EvaluateRequest(BaseModel):
    """
    Request to evaluate and compare two answers
    Used by Java: POST /ai/evaluate
    """

    question: str = Field(
        ...,
        description="Original question",
        example="Công thức nào là đúng?"
    )
    rag_answer: str = Field(
        ...,
        description="Answer from RAG pipeline",
        example="Công thức là..."
    )
    finetuned_answer: str = Field(
        ...,
        description="Answer from fine-tuned model",
        example="Công thức là..."
    )


class BenchmarkRequest(BaseModel):
    """
    Request to run RAGAS benchmark
    Used by Java: POST /ai/benchmark
    """

    experiment_id: str = Field(
        ...,
        description="Experiment ID for tracking",
        example="exp_001_e5base_k3"
    )
    questions: List[str] = Field(
        ...,
        description="List of test questions",
        example=["Câu 1?", "Câu 2?"]
    )
    config: dict = Field(
        ...,
        description="Benchmark configuration",
        example={
            "embedding_model": "BAAI/bge-m3",
            "chunk_size": 500,
            "top_k": 3,
            "chunking_strategy": "recursive"
        }
    )
 
