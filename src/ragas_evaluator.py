from __future__ import annotations

import asyncio
from typing import Any


def evaluate_batch(
    items: list[dict[str, Any]],
    *,
    api_key: str,
    evaluator_model: str,
    evaluator_embedding_model: str,
    concurrency: int = 2,
    max_retries: int = 3,
) -> dict[str, Any]:
    """Run official RAGAS 0.4.3 metrics and retain failures per question."""
    return asyncio.run(
        _evaluate_batch_async(
            items,
            api_key=api_key,
            evaluator_model=evaluator_model,
            evaluator_embedding_model=evaluator_embedding_model,
            concurrency=concurrency,
            max_retries=max_retries,
        )
    )


async def _evaluate_batch_async(
    items: list[dict[str, Any]],
    *,
    api_key: str,
    evaluator_model: str,
    evaluator_embedding_model: str,
    concurrency: int,
    max_retries: int,
) -> dict[str, Any]:
    from openai import AsyncOpenAI
    from ragas.embeddings import embedding_factory
    from ragas.llms import llm_factory
    from ragas.metrics.collections import (
        AnswerCorrectness,
        AnswerRelevancy,
        ContextPrecision,
        ContextRecall,
        Faithfulness,
    )

    client = AsyncOpenAI(api_key=api_key, max_retries=0)
    llm = llm_factory(evaluator_model, client=client)
    embeddings = embedding_factory(
        "openai",
        model=evaluator_embedding_model,
        client=client,
        interface="modern",
    )
    metrics = {
        "faithfulness": Faithfulness(llm=llm),
        "answer_relevancy": AnswerRelevancy(llm=llm, embeddings=embeddings),
        "answer_correctness": AnswerCorrectness(llm=llm, embeddings=embeddings),
        "context_precision": ContextPrecision(llm=llm),
        "context_recall": ContextRecall(llm=llm),
    }
    semaphore = asyncio.Semaphore(max(1, concurrency))

    async def evaluate_item(item: dict[str, Any]) -> dict[str, Any]:
        async with semaphore:
            for attempt in range(1, max_retries + 1):
                try:
                    values = await _score_item(item, metrics)
                    return {
                        "request_id": item["request_id"],
                        "status": "SUCCESS",
                        "error": None,
                        **values,
                    }
                except Exception as exc:
                    if attempt >= max_retries:
                        message = str(exc)
                        return {
                            "request_id": item["request_id"],
                            "status": "RATE_LIMITED" if "rate" in message.lower() else "FAILED",
                            "error": message[:2000],
                            "faithfulness": None,
                            "answer_relevancy": None,
                            "answer_correctness": None,
                            "context_precision": None,
                            "context_recall": None,
                        }
                    await asyncio.sleep(2 ** (attempt - 1))

    results = await asyncio.gather(*(evaluate_item(item) for item in items))
    await client.close()
    return {
        "official_ragas": True,
        "ragas_version": "0.4.3",
        "evaluator_model": evaluator_model,
        "evaluator_embedding_model": evaluator_embedding_model,
        "items": results,
    }


async def _score_item(item: dict[str, Any], metrics: dict[str, Any]) -> dict[str, float | None]:
    common = {
        "user_input": item["question"],
        "response": item["response"],
        "reference": item["reference"],
        "retrieved_contexts": item.get("retrieved_contexts") or [],
    }
    answer_relevancy = await metrics["answer_relevancy"].ascore(
        user_input=common["user_input"],
        response=common["response"],
        retrieved_contexts=common["retrieved_contexts"],
    )
    answer_correctness = await metrics["answer_correctness"].ascore(
        user_input=common["user_input"],
        response=common["response"],
        reference=common["reference"],
    )
    values: dict[str, float | None] = {
        "answer_relevancy": float(answer_relevancy.value),
        "answer_correctness": float(answer_correctness.value),
        "faithfulness": None,
        "context_precision": None,
        "context_recall": None,
    }
    if str(item.get("experiment_type", "RAG")).upper() != "RAG":
        return values

    faithfulness = await metrics["faithfulness"].ascore(
        user_input=common["user_input"],
        response=common["response"],
        retrieved_contexts=common["retrieved_contexts"],
    )
    context_precision = await metrics["context_precision"].ascore(
        user_input=common["user_input"],
        reference=common["reference"],
        retrieved_contexts=common["retrieved_contexts"],
    )
    context_recall = await metrics["context_recall"].ascore(
        user_input=common["user_input"],
        reference=common["reference"],
        retrieved_contexts=common["retrieved_contexts"],
    )
    values.update(
        faithfulness=float(faithfulness.value),
        context_precision=float(context_precision.value),
        context_recall=float(context_recall.value),
    )
    return values
