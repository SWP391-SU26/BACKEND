import csv
import shutil
import uuid
from pathlib import Path

from src.config import AppSettings
from src.embeddings import HashingEmbeddingProvider
from src.evaluation import BenchmarkRunner
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore


def test_benchmark_runner() -> None:
    root = Path.cwd() / "tests" / "_tmp" / str(uuid.uuid4())
    root.mkdir(parents=True, exist_ok=True)
    try:
        document = root / "demo.txt"
        document.write_text(
            "RAG la ky thuat ket hop truy hoi tai lieu va mo hinh sinh.",
            encoding="utf-8",
        )
        test_set = root / "test_set.csv"
        with test_set.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
                    "question",
                    "expected_answer",
                    "expected_source",
                    "is_out_of_scope",
                    "category",
                ],
            )
            writer.writeheader()
            writer.writerow(
                {
                    "question": "RAG la gi?",
                    "expected_answer": "RAG ket hop truy hoi tai lieu va mo hinh sinh.",
                    "expected_source": "demo.txt",
                    "is_out_of_scope": "false",
                    "category": "definition",
                }
            )

        settings = AppSettings(
            raw_dir=root,
            processed_dir=root,
            db_path=root / "test.sqlite3",
            reports_dir=root / "reports",
            chunk_size=40,
            chunk_overlap=5,
            min_retrieval_score=0.01,
            generation_provider="extractive",
        )
        store = SQLiteStore(settings.db_path)
        pipeline = RAGPipeline(settings, store, HashingEmbeddingProvider())
        pipeline.ingest_file(document, "AI", "RAG")

        result = BenchmarkRunner(pipeline, store).run(test_set)

        assert result["metrics"]["total_questions"] == 1
        assert result["metrics"]["source_hit_rate"] == 1.0
        assert "faithfulness_proxy" in result["metrics"]
        assert "context_recall_proxy" in result["metrics"]
        assert Path(result["csv_path"]).exists()
        assert store.get_benchmark_run(result["run_id"]) is not None
    finally:
        shutil.rmtree(root, ignore_errors=True)
