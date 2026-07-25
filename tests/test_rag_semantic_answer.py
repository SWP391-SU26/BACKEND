from pathlib import Path

from src.config import AppSettings
from src.rag_pipeline import RAGPipeline
from src.storage import RetrievedChunk


class DummyStore:
    pass


class ControlledEmbedding:
    model = "controlled-semantic-test"

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        for text in texts:
            if "đáp án đúng" in text.lower() or text == "Khái niệm cần hỏi là gì?":
                vectors.append([1.0, 0.0])
            else:
                vectors.append([0.0, 1.0])
        return vectors


def chunk(content: str, page: int, score: float = 0.7) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=str(page),
        document_id="doc",
        filename="subject.pdf",
        subject="Any subject",
        chapter="Any chapter",
        page=page,
        content=content,
        score=score,
        semantic_score=score,
    )


def test_semantic_passage_ranking_beats_superficial_keyword_overlap(tmp_path: Path) -> None:
    pipeline = RAGPipeline(
        AppSettings(lora_adapter_dir=tmp_path, generation_provider="extractive"),
        DummyStore(),
        ControlledEmbedding(),
    )
    answer = pipeline._generate_extractive_answer(
        "Khái niệm cần hỏi là gì?",
        [
            chunk("Khái niệm cần hỏi xuất hiện nhiều lần nhưng đoạn này không cung cấp nội dung.", 1),
            chunk("Đây là đáp án đúng giải thích bản chất của khái niệm bằng nội dung tài liệu.", 2),
        ],
        [],
    )

    assert "đáp án đúng" in answer


def test_comparison_selects_evidence_for_both_sides(tmp_path: Path) -> None:
    pipeline = RAGPipeline(
        AppSettings(lora_adapter_dir=tmp_path, generation_provider="extractive"),
        DummyStore(),
        ControlledEmbedding(),
    )
    candidates = [
        (0.8, "Biện chứng xem xét sự vật trong liên hệ và vận động.", chunk("", 1)),
        (0.7, "Siêu hình xem xét sự vật cô lập và tĩnh tại.", chunk("", 2)),
    ]

    selected = pipeline._select_answer_candidates(
        "So sánh phương pháp biện chứng và phương pháp siêu hình",
        "comparison",
        candidates,
    )

    assert any("Biện chứng" in item[1] for item in selected)
    assert any("Siêu hình" in item[1] for item in selected)
