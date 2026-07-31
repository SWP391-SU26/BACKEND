from src.grounded_answer import (
    ensure_grounded_answer,
    format_grounded_answer,
    select_context_windows,
)
from src.storage import RetrievedChunk


def chunk(chunk_id: str, content: str) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=chunk_id,
        document_id="doc-1",
        filename="triethoc.pdf",
        subject="Triet hoc Mac - Lenin",
        chapter="Chuong V",
        page=92,
        content=content,
        score=0.9,
        semantic_score=0.9,
        lexical_score=0.8,
    )


class SimilarEmbedding:
    def embed_texts(self, texts):
        return [[1.0, 0.0] for _text in texts]


def test_explanation_drops_methodology_from_causal_reasons() -> None:
    context = chunk(
        "chunk-1",
        "Vật chất có trước, ý thức có sau; vật chất là nguồn gốc và quyết định ý thức. "
        "Vì vậy con người phải tôn trọng khách quan và phát huy tính năng động chủ quan.",
    )
    generated = (
        "Vật chất quyết định ý thức vì vật chất có trước và là nguồn gốc của ý thức; "
        "con người phải tôn trọng khách quan và phát huy tính năng động chủ quan."
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        generated,
        [context],
        answer_profile="reasoning",
        embedding_provider=SimilarEmbedding(),
    )

    assert "Vật chất quyết định ý thức" in result.answer
    assert "tôn trọng khách quan" not in result.answer
    assert "năng động chủ quan" not in result.answer


def test_reasoning_context_excludes_methodology_guidance() -> None:
    context = chunk(
        "chunk-1",
        "Vật chất có trước và là nguồn gốc của ý thức. "
        "Con người phải tôn trọng khách quan. "
        "Ý thức tác động trở lại vật chất thông qua hoạt động thực tiễn.",
    )

    selected = select_context_windows(
        "Tại sao vật chất quyết định ý thức?",
        [context],
        answer_profile="reasoning",
        answer_depth="DEEP",
    )

    assert len(selected) == 1
    assert "nguồn gốc của ý thức" in selected[0].content
    assert "tôn trọng khách quan" not in selected[0].content


def test_explanation_drops_claims_that_reverse_the_requested_relation() -> None:
    context = chunk(
        "chunk-1",
        "Vật chất có trước và quyết định ý thức. "
        "Ý thức đúng đắn có thể định hướng hoạt động của con người.",
    )
    generated = (
        "Vật chất quyết định ý thức vì vật chất có trước. "
        "Ý thức đúng đắn quyết định hoạt động thành công của con người."
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        generated,
        [context],
        answer_profile="reasoning",
        embedding_provider=SimilarEmbedding(),
    )

    assert "vật chất có trước" in result.answer
    assert "quyết định hoạt động thành công" not in result.answer


def test_reasoning_formatter_removes_duplicate_fragments_and_reports_limit() -> None:
    formatted = format_grounded_answer(
        "**Trả lời trực tiếp:** Vật chất quyết định ý thức vì vật chất là nguồn gốc của ý thức.\n\n"
        "**Các lý do chính:**\n"
        "- vật chất có trước.\n"
        "- và vật chất là nguồn gốc của ý thức.\n"
        "- và \"vật chất có trước.",
        "reasoning",
        "Tại sao vật chất quyết định ý thức? Giải thích đầy đủ.",
    )

    assert formatted.count("vật chất có trước") == 1
    assert "vật chất là nguồn gốc của ý thức" in formatted
    assert "chưa đủ bằng chứng" in formatted
    assert '"' not in formatted
