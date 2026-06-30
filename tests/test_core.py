from pathlib import Path
import shutil
import uuid

from src.config import AppSettings
from src.embeddings import HashingEmbeddingProvider
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore


def test_ingest_and_answer_txt() -> None:
    root = Path.cwd() / "tests" / "_tmp" / str(uuid.uuid4())
    root.mkdir(parents=True, exist_ok=True)
    try:
        document = root / "demo.txt"
        document.write_text(
            "RAG la ky thuat ket hop truy hoi tai lieu va mo hinh sinh. "
            "He thong can trich dan nguon khi tra loi.",
            encoding="utf-8",
        )
        settings = AppSettings(
            raw_dir=root,
            processed_dir=root,
            db_path=root / "test.sqlite3",
            chunk_size=40,
            chunk_overlap=5,
            min_retrieval_score=0.01,
            generation_provider="extractive",
        )
        store = SQLiteStore(settings.db_path)
        pipeline = RAGPipeline(settings, store, HashingEmbeddingProvider())
        result = pipeline.ingest_file(document, "AI", "RAG")

        session_id = store.create_session()
        answer = pipeline.answer(session_id, "RAG la gi?")

        assert result.num_chunks >= 1
        assert "RAG" in answer.answer
        assert answer.sources
    finally:
        shutil.rmtree(root, ignore_errors=True)
