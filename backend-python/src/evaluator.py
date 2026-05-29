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
                model=s.llm_model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.3,
                max_tokens=200,
                timeout=30
            )
            judge_text = response.choices[0].message.content
        else:
            logger.warning("No LLM configured for judge, fallback to scores")
            judge_text = f"SCORE_A: {rag_score}\nSCORE_B: {finetuned_score}\nREASON: Fallback evaluation"

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

    """
    Factory function to create evaluator
    
    Args:
        model_name: Embedding model name
        
    Returns:
        AnswerEvaluator instance
    """
    return AnswerEvaluator(model_name)
 
