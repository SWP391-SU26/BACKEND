from src.grounded_answer import (
    answer_is_complete,
    answer_is_well_formed,
    ensure_grounded_answer,
    select_context_windows,
)
from src.storage import RetrievedChunk


def chunk(chunk_id: str, content: str, page: int = 10) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=chunk_id,
        document_id="document-1",
        filename="triethoc.pdf",
        subject="Triết học",
        chapter="Chương I",
        page=page,
        content=content,
        score=0.9,
        semantic_score=0.9,
        lexical_score=0.8,
    )


def test_supported_vietnamese_paraphrase_is_kept() -> None:
    class SimilarEmbedding:
        def embed_texts(self, texts):
            return [[1.0, 0.0] for _text in texts]

    context = chunk(
        "chunk-1",
        "Vật chất tồn tại khách quan, ở bên ngoài và không phụ thuộc vào ý thức.",
    )

    result = ensure_grounded_answer(
        "Tại sao nói vật chất quyết định ý thức?",
        "Vật chất có trước và tồn tại độc lập với ý thức, vì vậy ý thức phải dựa trên "
        "thế giới vật chất để hình thành.",
        [context],
        embedding_provider=SimilarEmbedding(),
    )

    assert result.answer
    assert result.used_chunk_ids == ["chunk-1"]
    assert result.unsupported_sentence_count == 0


def test_unsupported_clause_is_removed_instead_of_copying_context() -> None:
    context = chunk(
        "chunk-1",
        "Phương thức sản xuất là cách thức con người tiến hành sản xuất vật chất "
        "trong một giai đoạn lịch sử nhất định.",
    )
    generated = (
        "Phương thức sản xuất là cách con người tiến hành sản xuất vật chất. "
        "Nó luôn tạo ra phân biệt giàu nghèo và dùng máy móc hiện đại."
    )

    result = ensure_grounded_answer(
        "Phương thức sản xuất là gì?",
        generated,
        [context],
    )

    assert "Phương thức sản xuất" in result.answer
    assert "giàu nghèo" not in result.answer
    assert result.unsupported_sentence_count == 1
    assert not result.answer.startswith("Theo tài liệu")


def test_unsupported_comma_clause_is_removed_from_the_sentence() -> None:
    class SelectiveEmbedding:
        def embed_texts(self, texts):
            vectors = []
            for text in texts:
                normalized = text.casefold()
                vectors.append(
                    [0.0, 1.0] if "qua đời" in normalized else [1.0, 0.0]
                )
            return vectors

    context = chunk(
        "chunk-1",
        "Vật chất có trước, ý thức có sau. Ý thức là sự phản ánh thế giới vật chất "
        "vào bộ óc con người.",
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        "Vật chất quyết định ý thức, vì ý thức chỉ xuất hiện sau khi cơ thể con người qua đời.",
        [context],
        embedding_provider=SelectiveEmbedding(),
    )

    assert result.answer == "Vật chất quyết định ý thức."
    assert "qua đời" not in result.answer
    assert result.unsupported_sentence_count == 1


def test_grounding_failure_returns_empty_answer_not_raw_chunk() -> None:
    context = chunk(
        "chunk-1",
        "Giáo trình được xuất bản để phục vụ sinh viên trong quá trình học tập.",
    )

    result = ensure_grounded_answer(
        "Đội nào vô địch World Cup?",
        "Argentina won the latest tournament.",
        [context],
    )

    assert result.answer == ""
    assert result.used_chunk_ids == []
    assert result.unsupported_sentence_count == 1


def test_answer_must_still_address_the_question() -> None:
    context = chunk(
        "chunk-1",
        "Thomas Edison nghiên cứu nhiều thiết bị điện và góp phần phát triển kỹ thuật.",
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        "Thomas Edison nghiên cứu thiết bị điện và góp phần phát triển kỹ thuật.",
        [context],
    )

    assert result.answer == ""
    assert result.used_chunk_ids == []


def test_answer_must_preserve_core_academic_phrases() -> None:
    class SimilarEmbedding:
        def embed_texts(self, texts):
            return [[1.0, 0.0] for _text in texts]

    context = chunk(
        "chunk-1",
        "Vật chất có trước, là nguồn gốc của ý thức và quyết định ý thức.",
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        "Vật lý quyết định và kiểm soát ý trí trong đời sống con người.",
        [context],
        embedding_provider=SimilarEmbedding(),
    )

    assert result.answer == ""


def test_critical_number_must_exist_in_evidence() -> None:
    context = chunk(
        "chunk-1",
        "Vấn đề cơ bản của triết học có hai mặt.",
    )

    result = ensure_grounded_answer(
        "Vấn đề cơ bản của triết học có mấy mặt?",
        "Vấn đề cơ bản của triết học có ba mặt.",
        [context],
    )

    assert result.answer == ""
    assert result.unsupported_sentence_count == 1


def test_summary_context_ignores_toc_and_review_questions() -> None:
    contexts = [
        chunk("toc", "Mục lục ........ 1", page=1),
        chunk(
            "review",
            "Câu hỏi ôn tập: Vật chất là gì? Ý thức là gì? Quan hệ giữa chúng ra sao?",
            page=200,
        ),
        chunk(
            "content",
            "Chủ nghĩa duy vật biện chứng khẳng định vật chất có trước và ý thức có sau. "
            "Ý thức là sự phản ánh thế giới khách quan vào bộ óc con người.",
            page=81,
        ),
    ]

    selected = select_context_windows(
        "Nội dung tài liệu",
        contexts,
        answer_profile="summary",
    )

    assert [item.chunk_id for item in selected] == ["content"]


def test_context_window_keeps_relevant_sentence_and_neighbor() -> None:
    context = chunk(
        "chunk-1",
        "Triết học nghiên cứu những vấn đề chung. "
        "Vật chất quyết định ý thức vì vật chất có trước và là nguồn gốc của ý thức. "
        "Ý thức tác động trở lại vật chất thông qua hoạt động thực tiễn.",
    )

    selected = select_context_windows(
        "Tại sao vật chất quyết định ý thức?",
        [context],
        answer_profile="reasoning",
    )

    assert len(selected) == 1
    assert "Vật chất quyết định ý thức" in selected[0].content
    assert "hoạt động thực tiễn" in selected[0].content


def test_reasoning_context_drops_unrequested_historical_background() -> None:
    contexts = [
        chunk(
            "history",
            "Trong lịch sử triết học, các nhà triết học tranh luận giữa nhiều trường phái. "
            "Chủ nghĩa duy tâm cho rằng ý thức có trước vật chất.",
        ),
        chunk(
            "evidence",
            "Vật chất có trước và là nguồn gốc của ý thức. "
            "Ý thức phụ thuộc vào vật chất và có thể tác động trở lại thông qua thực tiễn.",
        ),
    ]

    selected = select_context_windows(
        "Tại sao vật chất quyết định ý thức?",
        contexts,
        answer_profile="reasoning",
    )

    assert [item.chunk_id for item in selected] == ["evidence"]


def test_grounded_fragment_does_not_pass_completeness_gate() -> None:
    answer = "Vật chất quyết định ý thức vì vật chất là nguồn gốc của ý thức."

    assert answer_is_complete(answer, "reasoning") is False


def test_complete_reasoning_answer_passes_completeness_gate() -> None:
    answer = (
        "Vật chất quyết định ý thức vì vật chất có trước và tồn tại khách quan. "
        "Ý thức hình thành trên cơ sở bộ óc con người và sự tác động của thế giới khách quan. "
        "Nội dung của ý thức vì thế chịu sự quy định của điều kiện vật chất và hoạt động thực tiễn. "
        "Tuy nhiên, ý thức vẫn có thể tác động trở lại vật chất thông qua hoạt động của con người."
    )

    assert answer_is_complete(answer, "reasoning") is True


def test_fully_grounded_markdown_is_preserved() -> None:
    class SimilarEmbedding:
        def embed_texts(self, texts):
            return [[1.0, 0.0] for _text in texts]

    context = chunk(
        "chunk-1",
        "Vật chất tồn tại khách quan. Ý thức là sự phản ánh thế giới vật chất.",
    )
    generated = (
        "- Vật chất tồn tại khách quan.\n"
        "- Ý thức là sự phản ánh thế giới vật chất."
    )

    result = ensure_grounded_answer(
        "Nêu quan hệ giữa vật chất và ý thức.",
        generated,
        [context],
        embedding_provider=SimilarEmbedding(),
    )

    assert result.answer == generated


def test_repetitive_synonym_chain_is_not_well_formed() -> None:
    answer = (
        "Ý thức, ý trí, ý kiến, ý định, ý niệm, ý muốn, ý hướng, ý nguyện, "
        "ý tưởng, ý luận đều do vật lý kiểm soát."
    )

    assert answer_is_well_formed(answer) is False


def test_explanation_drops_unrequested_historical_attribution() -> None:
    class SimilarEmbedding:
        def embed_texts(self, texts):
            return [[1.0, 0.0] for _text in texts]

    context = chunk(
        "chunk-1",
        "Vật chất có trước và quyết định ý thức. Hegel là một nhà triết học.",
    )
    generated = (
        "Vật chất quyết định ý thức vì vật chất có trước và là nguồn gốc của ý thức. "
        "Hegel đã xây dựng quan điểm này trong lịch sử triết học."
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        generated,
        [context],
        embedding_provider=SimilarEmbedding(),
    )

    assert "Vật chất quyết định ý thức" in result.answer
    assert "Hegel" not in result.answer


def test_explanation_drops_historical_background_sentence() -> None:
    class SimilarEmbedding:
        def embed_texts(self, texts):
            return [[1.0, 0.0] for _text in texts]

    context = chunk(
        "chunk-1",
        "Vật chất có trước và quyết định ý thức. Do khoa học chưa phát triển, "
        "các nhà duy vật trước Mác chịu ảnh hưởng của quan điểm siêu hình.",
    )
    generated = (
        "Vật chất quyết định ý thức vì vật chất có trước và là nguồn gốc của ý thức. "
        "Do khoa học chưa phát triển, quan điểm siêu hình đã tồn tại trong lịch sử."
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        generated,
        [context],
        embedding_provider=SimilarEmbedding(),
    )

    assert "Vật chất quyết định ý thức" in result.answer
    assert "khoa học chưa phát triển" not in result.answer
