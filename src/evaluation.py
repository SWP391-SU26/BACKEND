from __future__ import annotations

import csv
import time
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any

from .rag_pipeline import OUT_OF_SCOPE_MESSAGE, RAGPipeline
from .storage import SQLiteStore
from .text_utils import tokenize


@dataclass(frozen=True)
class TestCase:
    question: str
    expected_answer: str
    expected_source: str
    expected_page: int | None
    subject: str
    is_out_of_scope: bool
    category: str


class BenchmarkRunner:
    def __init__(self, pipeline: RAGPipeline, store: SQLiteStore) -> None:
        self.pipeline = pipeline
        self.store = store

    def run(self, test_set_path: Path, mode: str = "rag") -> dict[str, Any]:
        mode = normalize_mode(mode)
        cases = load_test_set(test_set_path)
        if not cases:
            raise ValueError("Test set không có câu hỏi hợp lệ.")

        results = [self._evaluate_case(case, mode) for case in cases]
        metrics = aggregate_metrics(results)
        metrics["benchmark_mode"] = mode
        metrics["generation_provider"] = self.pipeline.settings.generation_provider
        run_id = self.store.add_benchmark_run(
            test_set_path=str(test_set_path),
            embedding_model=self.pipeline.embedding_provider.model,
            top_k=self.pipeline.settings.top_k,
            metrics=metrics,
            results=results,
        )
        csv_path = self.pipeline.settings.reports_dir / f"benchmark_{run_id}.csv"
        write_results_csv(csv_path, results)
        return {
            "run_id": run_id,
            "metrics": metrics,
            "results": results,
            "csv_path": str(csv_path),
        }

    def _evaluate_case(self, case: TestCase, mode: str) -> dict[str, Any]:
        started = time.perf_counter()
        if mode == "finetuned_only":
            actual_answer = self.pipeline.generate_without_retrieval(case.question)
            sources: list[dict[str, Any]] = []
            retrieved = []
        else:
            session_id = self.store.create_session("Benchmark")
            subject = None if case.is_out_of_scope else case.subject
            response = self.pipeline.answer(session_id, case.question, subject=subject)
            actual_answer = response.answer
            sources = response.sources
            retrieved = response.retrieved
        latency_ms = round((time.perf_counter() - started) * 1000, 2)
        refused = actual_answer.strip() == OUT_OF_SCOPE_MESSAGE
        source_hit = source_matches(case.expected_source, sources) if mode != "finetuned_only" else False
        page_hit = page_matches(case.expected_page, sources) if mode != "finetuned_only" else False
        answer_f1 = token_f1(actual_answer, case.expected_answer)
        refusal_correct = refused == case.is_out_of_scope
        top_score = retrieved[0].score if retrieved else 0.0
        context_texts = [item.content for item in retrieved]

        return {
            "benchmark_mode": mode,
            "question": case.question,
            "category": case.category,
            "expected_answer": case.expected_answer,
            "actual_answer": actual_answer,
            "expected_source": case.expected_source,
            "expected_page": case.expected_page,
            "subject": case.subject,
            "is_out_of_scope": case.is_out_of_scope,
            "refused": refused,
            "refusal_correct": refusal_correct,
            "answer_token_f1": round(answer_f1, 4),
            "source_hit": source_hit,
            "page_hit": page_hit,
            "top_retrieval_score": round(top_score, 4),
            "faithfulness_proxy": round(faithfulness_proxy(actual_answer, context_texts), 4),
            "answer_relevancy_proxy": round(answer_f1, 4),
            "context_precision_proxy": round(
                context_precision_proxy(case.expected_answer, context_texts), 4
            ),
            "context_recall_proxy": round(context_recall_proxy(case.expected_answer, context_texts), 4),
            "latency_ms": latency_ms,
        }


def load_test_set(path: Path) -> list[TestCase]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = csv.DictReader(handle)
        required = {"question", "expected_answer", "expected_source", "is_out_of_scope"}
        if not rows.fieldnames or not required.issubset(rows.fieldnames):
            raise ValueError(f"CSV phải có các cột: {', '.join(sorted(required))}")
        return [
            TestCase(
                question=(row.get("question") or "").strip(),
                expected_answer=(row.get("expected_answer") or "").strip(),
                expected_source=(row.get("expected_source") or "").strip(),
                expected_page=parse_optional_int(row.get("expected_page") or ""),
                subject=(row.get("subject") or "").strip(),
                is_out_of_scope=parse_bool(row.get("is_out_of_scope") or ""),
                category=(row.get("category") or "general").strip(),
            )
            for row in rows
            if (row.get("question") or "").strip()
        ]


def token_f1(actual: str, expected: str) -> float:
    actual_tokens = tokenize(actual)
    expected_tokens = tokenize(expected)
    if not expected_tokens:
        return 1.0 if not actual_tokens else 0.0
    if not actual_tokens:
        return 0.0
    common = sum((Counter(actual_tokens) & Counter(expected_tokens)).values())
    if not common:
        return 0.0
    precision = common / len(actual_tokens)
    recall = common / len(expected_tokens)
    return 2 * precision * recall / (precision + recall)


def source_matches(expected_source: str, sources: list[dict[str, Any]]) -> bool:
    if not expected_source:
        return True
    expected = expected_source.lower()
    return any(expected in str(source.get("filename", "")).lower() for source in sources)


def page_matches(expected_page: int | None, sources: list[dict[str, Any]]) -> bool:
    if expected_page is None:
        return True
    return any(source.get("page") == expected_page for source in sources)


def aggregate_metrics(results: list[dict[str, Any]]) -> dict[str, Any]:
    in_scope = [item for item in results if not item["is_out_of_scope"]]
    return {
        "total_questions": len(results),
        "answer_token_f1": round(mean(item["answer_token_f1"] for item in results), 4),
        "source_hit_rate": round(
            mean(item["source_hit"] for item in in_scope), 4
        ) if in_scope else 0.0,
        "page_hit_rate": round(
            mean(item["page_hit"] for item in in_scope), 4
        ) if in_scope else 0.0,
        "refusal_accuracy": round(mean(item["refusal_correct"] for item in results), 4),
        "average_top_retrieval_score": round(
            mean(item["top_retrieval_score"] for item in results), 4
        ),
        "average_latency_ms": round(mean(item["latency_ms"] for item in results), 2),
        "faithfulness_proxy": metric_mean(in_scope, "faithfulness_proxy"),
        "answer_relevancy_proxy": metric_mean(in_scope, "answer_relevancy_proxy"),
        "context_precision_proxy": metric_mean(in_scope, "context_precision_proxy"),
        "context_recall_proxy": metric_mean(in_scope, "context_recall_proxy"),
    }


def write_results_csv(path: Path, results: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(results[0].keys()))
        writer.writeheader()
        writer.writerows(results)


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "y", "co", "có"}


def parse_optional_int(value: str) -> int | None:
    value = value.strip()
    return int(value) if value else None


def normalize_mode(mode: str) -> str:
    aliases = {
        "rag": "rag",
        "rag_lora": "rag",
        "rag_extractive": "rag",
        "finetuned_only": "finetuned_only",
        "fine_tuned_only": "finetuned_only",
    }
    normalized = aliases.get(mode.strip().lower())
    if not normalized:
        raise ValueError("Benchmark mode phải là rag hoặc finetuned_only.")
    return normalized


def faithfulness_proxy(answer: str, contexts: list[str]) -> float:
    answer_tokens = meaningful_token_set(answer)
    context_tokens = meaningful_token_set(" ".join(contexts))
    if not answer_tokens:
        return 0.0
    return len(answer_tokens & context_tokens) / len(answer_tokens)


def context_recall_proxy(expected_answer: str, contexts: list[str]) -> float:
    expected_tokens = meaningful_token_set(expected_answer)
    context_tokens = meaningful_token_set(" ".join(contexts))
    if not expected_tokens:
        return 0.0
    return len(expected_tokens & context_tokens) / len(expected_tokens)


def context_precision_proxy(expected_answer: str, contexts: list[str]) -> float:
    expected_tokens = meaningful_token_set(expected_answer)
    if not expected_tokens or not contexts:
        return 0.0
    relevant = sum(bool(expected_tokens & meaningful_token_set(context)) for context in contexts)
    return relevant / len(contexts)


def meaningful_token_set(text: str) -> set[str]:
    stop_words = {
        "là", "gì", "và", "có", "được", "như", "thế", "nào", "trong", "theo",
        "những", "các", "của", "về", "tại", "để", "một", "cho", "khi", "từ",
    }
    return {token for token in tokenize(text) if len(token) > 1 and token not in stop_words}


def metric_mean(rows: list[dict[str, Any]], key: str) -> float:
    return round(mean(item[key] for item in rows), 4) if rows else 0.0
