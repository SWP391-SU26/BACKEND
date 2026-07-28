from __future__ import annotations

import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .finetuning import normalize_source_name, refusal_questions_by_source, training_questions_by_source


@dataclass(frozen=True)
class FineTunedScopeDecision:
    allowed: bool
    confidence: float
    matched_source: str | None = None


class FineTunedScopeGuard:
    """Semantic classifier over training questions; it never injects document text into generation."""

    STOPWORDS = {
        "là", "gì", "điều", "nào", "những", "các", "của", "và", "trong", "theo",
        "hãy", "giải", "thích", "giúp", "mình", "bạn", "được", "không", "phát", "biểu",
        "như", "thế", "cách", "về", "cho", "biết", "trình", "bày",
    }

    def __init__(self, dataset_paths: list[Path], embedding_provider: Any, min_similarity: float = 0.60) -> None:
        self.questions_by_source = training_questions_by_source(dataset_paths)
        self.refusals_by_source = refusal_questions_by_source(dataset_paths)
        self.embedding_provider = embedding_provider
        self.min_similarity = min_similarity
        self._vectors_by_source: dict[str, list[list[float]]] = {}
        self._refusal_vectors_by_source: dict[str, list[list[float]]] = {}

    def decide(self, question: str, selected_sources: list[str]) -> FineTunedScopeDecision:
        selected = {normalize_source_name(source) for source in selected_sources if source and source.strip()}
        candidates: list[tuple[str, str]] = [
            (source, training_question)
            for source in selected
            for training_question in self.questions_by_source.get(source, [])
        ]
        if not question.strip() or not candidates:
            return FineTunedScopeDecision(False, 0.0)

        query_terms = self._terms(question)
        best_lexical = 0.0
        for _source, candidate in candidates:
            terms = self._terms(candidate)
            if terms:
                best_lexical = max(best_lexical, len(query_terms & terms) / max(1, len(query_terms | terms)))

        query_vector = self.embedding_provider.embed_query(question)
        best_semantic = 0.0
        best_source: str | None = None
        for source in selected:
            questions = self.questions_by_source.get(source, [])
            if not questions:
                continue
            vectors = self._vectors_by_source.get(source)
            if vectors is None:
                vectors = self.embedding_provider.embed_texts(questions)
                self._vectors_by_source[source] = vectors
            for vector in vectors:
                score = self._cosine(query_vector, vector)
                if score > best_semantic:
                    best_semantic = score
                    best_source = source

        best_refusal = 0.0
        for source in selected:
            refusal_questions = self.refusals_by_source.get(source, [])
            if not refusal_questions:
                continue
            vectors = self._refusal_vectors_by_source.get(source)
            if vectors is None:
                vectors = self.embedding_provider.embed_texts(refusal_questions)
                self._refusal_vectors_by_source[source] = vectors
            best_refusal = max(
                best_refusal,
                max((self._cosine(query_vector, vector) for vector in vectors), default=0.0),
            )

        confidence = max(best_semantic, min(1.0, best_lexical * 1.7))
        looks_like_known_refusal = best_refusal >= 0.72 or best_refusal > best_semantic + 0.03
        allowed = not looks_like_known_refusal and (
            best_semantic >= self.min_similarity or best_lexical >= 0.34
        )
        return FineTunedScopeDecision(allowed, round(confidence, 4), best_source)

    @staticmethod
    def _terms(text: str) -> set[str]:
        return {
            token
            for token in re.findall(r"[^\W_]+", text.casefold(), flags=re.UNICODE)
            if len(token) > 1 and token not in FineTunedScopeGuard.STOPWORDS
        }

    @staticmethod
    def _cosine(left: list[float], right: list[float]) -> float:
        numerator = sum(a * b for a, b in zip(left, right))
        left_norm = math.sqrt(sum(value * value for value in left))
        right_norm = math.sqrt(sum(value * value for value in right))
        if not left_norm or not right_norm:
            return 0.0
        return numerator / (left_norm * right_norm)
