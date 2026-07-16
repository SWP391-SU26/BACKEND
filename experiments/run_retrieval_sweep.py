from __future__ import annotations

import argparse
import csv
import json
import sys
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.config import ensure_data_dirs, load_settings
from src.embeddings import get_embedding_provider
from src.evaluation import BenchmarkRunner
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore


def main() -> None:
    parser = argparse.ArgumentParser(description="Sweep hybrid retrieval configurations.")
    parser.add_argument("--test-set", default=str(ROOT / "data" / "test_set.csv"))
    parser.add_argument("--semantic-weights", default="0.4,0.55,0.65,0.8")
    parser.add_argument("--thresholds", default="0.06,0.08,0.1")
    parser.add_argument("--top-k", default="3,4,5")
    parser.add_argument("--embedding-provider", default=None)
    parser.add_argument("--embedding-model", default=None)
    args = parser.parse_args()

    base_settings = load_settings()
    base_settings = replace(
        base_settings,
        embedding_provider=args.embedding_provider or base_settings.embedding_provider,
        embedding_model=args.embedding_model or base_settings.embedding_model,
    )
    ensure_data_dirs(base_settings)
    store = SQLiteStore(base_settings.db_path)
    provider = get_embedding_provider(base_settings)
    rows = []

    for semantic_weight in parse_floats(args.semantic_weights):
        for threshold in parse_floats(args.thresholds):
            for top_k in parse_ints(args.top_k):
                settings = replace(
                    base_settings,
                    semantic_weight=semantic_weight,
                    min_retrieval_score=threshold,
                    top_k=top_k,
                )
                pipeline = RAGPipeline(settings, store, provider)
                result = BenchmarkRunner(pipeline, store).run(Path(args.test_set).resolve())
                metrics = result["metrics"]
                rows.append(
                    {
                        "run_id": result["run_id"],
                        "semantic_weight": semantic_weight,
                        "lexical_weight": round(1 - semantic_weight, 4),
                        "threshold": threshold,
                        "top_k": top_k,
                        **metrics,
                        "quality_score": quality_score(metrics),
                    }
                )

    rows.sort(key=lambda item: item["quality_score"], reverse=True)
    output_path = base_settings.reports_dir / "retrieval_sweep.csv"
    with output_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(json.dumps({"best": rows[0], "output_path": str(output_path)}, ensure_ascii=False, indent=2))


def quality_score(metrics: dict) -> float:
    return round(
        (
            metrics["source_hit_rate"]
            + metrics["page_hit_rate"]
            + metrics["refusal_accuracy"]
            + metrics["answer_token_f1"]
        )
        / 4,
        4,
    )


def parse_floats(value: str) -> list[float]:
    return [float(item.strip()) for item in value.split(",") if item.strip()]


def parse_ints(value: str) -> list[int]:
    return [int(item.strip()) for item in value.split(",") if item.strip()]


if __name__ == "__main__":
    main()
