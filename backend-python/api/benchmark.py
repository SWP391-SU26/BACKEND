"""POST /ai/benchmark — Chạy RAGAS evaluation cho experiment"""

from fastapi import APIRouter, HTTPException
from loguru import logger
from models.requests import BenchmarkRequest
from models.responses import BenchmarkResponse, RAGASResult, BenchmarkSummary
from src.rag_pipeline import run_rag
import time

router = APIRouter()


@router.post("/benchmark", response_model=BenchmarkResponse)
async def benchmark(request: BenchmarkRequest):
    """
    Chạy toàn bộ test set qua RAG pipeline
    Tính RAGAS metrics cho từng câu
    """
    logger.info(f"Benchmark experiment {request.experiment_id}: {len(request.questions)} questions")

    results = []
    generated_answers = []
    retrieved_contexts_list = []

    # Bước 1: Chạy RAG cho từng câu
    for q in request.questions:
        try:
            start = time.time()
            rag_result = await run_rag(
                question=q.question,
                collection_name=request.config.collection_name,
                embedding_model=request.config.embedding_model.value,
                top_k=request.config.top_k,
                similarity_threshold=request.config.similarity_threshold,
                conversation_history=[]
            )
            latency = int((time.time() - start) * 1000)

            results.append({
                "question_id": q.question_id,
                "question": q.question,
                "ground_truth": q.ground_truth,
                "generated_answer": rag_result["rag_answer"],
                "citations": rag_result["citations"],
                "latency_ms": latency
            })

            generated_answers.append(rag_result["rag_answer"])
            retrieved_contexts = [c["excerpt"] for c in rag_result["citations"]]
            retrieved_contexts_list.append(retrieved_contexts)

        except Exception as e:
            logger.error(f"Error processing question {q.question_id}: {e}")
            results.append({
                "question_id": q.question_id,
                "question": q.question,
                "ground_truth": q.ground_truth,
                "generated_answer": f"Error: {str(e)}",
                "citations": [],
                "latency_ms": 0
            })

    # Bước 2: Tính RAGAS metrics
    ragas_results = await _compute_ragas(results, generated_answers, retrieved_contexts_list)

    # Bước 3: Tính summary
    summary = _compute_summary(ragas_results)

    return BenchmarkResponse(
        experiment_id=request.experiment_id,
        results=ragas_results,
        summary=summary
    )


async def _compute_ragas(results: list[dict], generated_answers: list[str], retrieved_contexts_list: list[list[str]]) -> list[RAGASResult]:
    """Tính RAGAS metrics — wrapper để dễ mock trong test"""
    try:
        from ragas import evaluate
        from ragas.metrics import (
            faithfulness,
            answer_relevancy,
            context_precision,
            context_recall
        )
        from datasets import Dataset

        # Chuẩn bị data cho RAGAS
        eval_data = {
            "question": [r["question"] for r in results],
            "answer": generated_answers,
            "contexts": retrieved_contexts_list,
            "ground_truth": [r["ground_truth"] for r in results]
        }

        dataset = Dataset.from_dict(eval_data)

        # Chạy RAGAS evaluation
        score = await evaluate(
            dataset,
            metrics=[
                faithfulness,
                answer_relevancy,
                context_precision,
                context_recall
            ]
        )

        # Ghép kết quả
        ragas_list = []
        for i, r in enumerate(results):
            ragas_result = RAGASResult(
                question_id=r["question_id"],
                generated_answer=r["generated_answer"],
                faithfulness=float(score["faithfulness"][i]) if "faithfulness" in score else None,
                answer_relevancy=float(score["answer_relevancy"][i]) if "answer_relevancy" in score else None,
                context_precision=float(score["context_precision"][i]) if "context_precision" in score else None,
                context_recall=float(score["context_recall"][i]) if "context_recall" in score else None,
                latency_ms=r["latency_ms"]
            )
            ragas_list.append(ragas_result)

        return ragas_list

    except Exception as e:
        logger.error(f"RAGAS evaluation failed: {e}")
        raise HTTPException(status_code=500, detail={"code": "BM-002", "message": str(e)})


def _compute_summary(results: list[RAGASResult]) -> BenchmarkSummary:
    """Tính average metrics"""
    valid = [r for r in results if r.faithfulness is not None]
    if not valid:
        return BenchmarkSummary(
            avg_faithfulness=0,
            avg_answer_relevancy=0,
            avg_context_precision=0,
            avg_context_recall=0
        )
    n = len(valid)
    return BenchmarkSummary(
        avg_faithfulness=sum(r.faithfulness for r in valid) / n,
        avg_answer_relevancy=sum(r.answer_relevancy for r in valid) / n,
        avg_context_precision=sum(r.context_precision for r in valid) / n,
        avg_context_recall=sum(r.context_recall for r in valid) / n
    )
 
