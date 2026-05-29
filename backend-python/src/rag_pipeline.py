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
            messages=messages,
            system=system,
            temperature=0.7,
            max_tokens=1024,
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
            "excerpt": c["content"][:150],
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

