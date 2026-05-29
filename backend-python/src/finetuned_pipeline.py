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
            "temperature": 0.7,
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
        logger.info(f"Fine-tuned inference: latency={latency}ms")

        return {
            "finetuned_answer": answer,
            "finetuned_score": 0.8,  # Default score
            "latency_ms": latency
        }

    except httpx.TimeoutException:
        raise HTTPException(status_code=504, detail={"code": "FT-002", "message": "Fine-tuned model timeout"})
    except Exception as e:
        logger.error(f"Fine-tuned error: {e}")
        raise HTTPException(status_code=502, detail={"code": "FT-001", "message": str(e)})

