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
 
