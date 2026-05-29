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
 
