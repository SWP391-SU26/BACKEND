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
 
