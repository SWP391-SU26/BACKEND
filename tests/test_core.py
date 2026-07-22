from pathlib import Path
import shutil
import uuid

from src.config import AppSettings
from src.embeddings import HashingEmbeddingProvider
from src.rag_pipeline import OUT_OF_SCOPE_MESSAGE, RAGPipeline
from src.storage import RetrievedChunk, SQLiteStore


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


def test_extractive_answer_uses_previous_question_for_follow_up(tmp_path: Path) -> None:
    settings = AppSettings(
        raw_dir=tmp_path,
        processed_dir=tmp_path,
        db_path=tmp_path / "test.sqlite3",
        generation_provider="extractive",
    )
    pipeline = RAGPipeline(settings, SQLiteStore(settings.db_path), HashingEmbeddingProvider())
    contexts = [
        RetrievedChunk(
            chunk_id="chunk-1",
            document_id="doc-1",
            filename="math.pdf",
            subject="Math",
            chapter="Ratios",
            page=2,
            content=(
                "Mot hinh chu nhat co chieu rong bang 2/3 chieu dai va kem chieu dai 15m. "
                "Thong tin quang cao khong lien quan den bai toan."
            ),
            score=0.9,
        )
    ]

    answer = pipeline._generate_answer(
        "Giải thích thêm",
        contexts,
        [],
        conversation_history=[
            {"role": "user", "content": "Chieu rong bang bao nhieu phan chieu dai?"},
            {"role": "assistant", "content": "Chieu rong bang 2/3 chieu dai."},
        ],
    )

    assert "2/3" in answer
    assert "Thong tin quang cao" not in answer
    assert pipeline._extractive_question(
        "Thủ đô của Pháp là gì?",
        [{"role": "user", "content": "Chieu rong bang bao nhieu phan chieu dai?"}],
    ) == "Thủ đô của Pháp là gì?"
    assert pipeline._generate_extractive_answer(
        "Thủ đô của Pháp là gì?",
        [
            RetrievedChunk(
                chunk_id="chunk-2",
                document_id="doc-2",
                filename="history.pdf",
                subject="History",
                chapter="France",
                page=1,
                content="Nuoc Phap co nhieu bien dong trong lich su hien dai.",
                score=0.9,
            )
        ],
        [],
    ) == OUT_OF_SCOPE_MESSAGE
