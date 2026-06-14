from __future__ import annotations

import argparse
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
    parser = argparse.ArgumentParser(description="Run RAG chatbot benchmark.")
    parser.add_argument(
        "--test-set",
        default=str(ROOT / "data" / "test_set.csv"),
        help="Path to benchmark CSV.",
    )
    parser.add_argument("--embedding-provider", default=None)
    parser.add_argument("--embedding-model", default=None)
    parser.add_argument("--generation-provider", default=None)
    parser.add_argument("--mode", default="rag", choices=["rag", "finetuned_only"])
    args = parser.parse_args()

    settings = load_settings()
    settings = replace(
        settings,
        embedding_provider=args.embedding_provider or settings.embedding_provider,
        embedding_model=args.embedding_model or settings.embedding_model,
        generation_provider=args.generation_provider or settings.generation_provider,
    )
    ensure_data_dirs(settings)
    store = SQLiteStore(settings.db_path)
    pipeline = RAGPipeline(settings, store, get_embedding_provider(settings))
    result = BenchmarkRunner(pipeline, store).run(Path(args.test_set).resolve(), mode=args.mode)
    print(json.dumps({"run_id": result["run_id"], "metrics": result["metrics"]}, ensure_ascii=False, indent=2))
    print(f"CSV: {result['csv_path']}")


if __name__ == "__main__":
    main()
