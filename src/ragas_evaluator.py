from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from .embeddings import EmbeddingProvider


@dataclass(frozen=True)
class RagasScores:
    faithfulness: float | None
    answer_relevancy: float
    context_precision: float | None
    context_recall: float | None
    judge_model: str
    embedding_model: str
    prompt_version: str


class BgeM3RagasEmbedding:
    """Lazy adapter around the project's BGE-M3 provider for RAGAS."""

    def __new__(cls, provider: EmbeddingProvider):
        from ragas.embeddings.base import BaseRagasEmbedding

        class Adapter(BaseRagasEmbedding):
            def embed_text(self, text: str, **_kwargs: Any) -> list[float]:
                return provider.embed_query(text)

            async def aembed_text(
                self, text: str, **_kwargs: Any
            ) -> list[float]:
                return await asyncio.to_thread(provider.embed_query, text)

            def embed_texts(
                self, texts: list[str], **_kwargs: Any
            ) -> list[list[float]]:
                return provider.embed_texts(texts)

            async def aembed_texts(
                self, texts: list[str], **_kwargs: Any
            ) -> list[list[float]]:
                return await asyncio.to_thread(provider.embed_texts, texts)

        return Adapter()


class OfficialRagasEvaluator:
    def __init__(
        self,
        *,
        api_key: str,
        judge_model: str,
        embedding_provider: EmbeddingProvider,
        prompt_version: str,
    ) -> None:
        from openai import AsyncOpenAI
        from ragas.llms.base import llm_factory
        from ragas.metrics.collections import (
            AnswerRelevancy,
            ContextPrecision,
            ContextRecall,
            Faithfulness,
        )

        client = AsyncOpenAI(api_key=api_key)
        llm = llm_factory(judge_model, provider="openai", client=client)
        embeddings = BgeM3RagasEmbedding(embedding_provider)
        self.faithfulness = Faithfulness(llm=llm)
        self.answer_relevancy = AnswerRelevancy(
            llm=llm, embeddings=embeddings, strictness=1
        )
        self.context_precision = ContextPrecision(llm=llm)
        self.context_recall = ContextRecall(llm=llm)
        self.judge_model = judge_model
        self.embedding_model = embedding_provider.model
        self.prompt_version = prompt_version

    async def evaluate(
        self,
        *,
        question: str,
        response: str,
        contexts: list[str],
        reference: str,
    ) -> RagasScores:
        if not contexts:
            answer_relevancy = await self.answer_relevancy.ascore(
                user_input=question,
                response=response,
            )
            return RagasScores(
                faithfulness=None,
                answer_relevancy=score_value(answer_relevancy),
                context_precision=None,
                context_recall=None,
                judge_model=self.judge_model,
                embedding_model=self.embedding_model,
                prompt_version=self.prompt_version,
            )
        faithfulness, answer_relevancy, context_precision, context_recall = (
            await asyncio.gather(
                self.faithfulness.ascore(
                    user_input=question,
                    response=response,
                    retrieved_contexts=contexts,
                ),
                self.answer_relevancy.ascore(
                    user_input=question,
                    response=response,
                ),
                self.context_precision.ascore(
                    user_input=question,
                    reference=reference,
                    retrieved_contexts=contexts,
                ),
                self.context_recall.ascore(
                    user_input=question,
                    reference=reference,
                    retrieved_contexts=contexts,
                ),
            )
        )
        return RagasScores(
            faithfulness=score_value(faithfulness),
            answer_relevancy=score_value(answer_relevancy),
            context_precision=score_value(context_precision),
            context_recall=score_value(context_recall),
            judge_model=self.judge_model,
            embedding_model=self.embedding_model,
            prompt_version=self.prompt_version,
        )


def score_value(result: Any) -> float:
    value = getattr(result, "value", result)
    return round(float(value), 6)
