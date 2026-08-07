from src.grounded_answer import (
    answer_completeness_issues,
    answer_is_complete,
    answer_is_well_formed,
    ensure_grounded_answer,
    extract_explicit_definition,
    extract_historical_origin,
    format_grounded_answer,
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


def test_partial_grounding_keeps_supported_bullet_structure() -> None:
    class SimilarEmbedding:
        def embed_texts(self, texts):
            return [[1.0, 0.0] for _text in texts]

    context = chunk(
        "chunk-1",
        "Vật chất tồn tại khách quan và có trước ý thức. "
        "Ý thức là sự phản ánh thế giới vật chất.",
    )
    generated = (
        "- Vật chất tồn tại khách quan và có trước ý thức.\n"
        "- Hegel đã xây dựng toàn bộ quan điểm này.\n"
        "- Ý thức là sự phản ánh thế giới vật chất."
    )

    result = ensure_grounded_answer(
        "Tại sao vật chất quyết định ý thức?",
        generated,
        [context],
        embedding_provider=SimilarEmbedding(),
    )

    assert result.answer.count("\n- ") == 1
    assert result.answer.startswith("- ")
    assert "Vật chất tồn tại khách quan" in result.answer
    assert "Ý thức là sự phản ánh" in result.answer
    assert "Hegel" not in result.answer


def test_partial_markdown_table_falls_back_to_valid_bullets() -> None:
    supported = [
        "Vật chất tồn tại khách quan.",
        "Ý thức phản ánh thế giới vật chất.",
    ]
    original = (
        "| Tiêu chí | Vật chất | Ý thức |\n"
        "|---|---|---|\n"
        "| Bản chất | Tồn tại khách quan. | Phản ánh thế giới vật chất. |"
    )

    from src.grounded_answer import preserve_supported_markdown

    result = preserve_supported_markdown(original, supported)

    assert result == (
        "- Vật chất tồn tại khách quan.\n"
        "- Ý thức phản ánh thế giới vật chất."
    )


def test_reasoning_formatter_recovers_inline_numbered_model_output() -> None:
    answer = (
        "Vật chất quyết định ý thức.\n"
        "2. Vật chất là nguồn gốc của ý thức.\n"
        "3. Ý thức tác động trở lại vật chất thông qua thực tiễn."
    )

    result = format_grounded_answer(
        answer,
        "reasoning",
        "Tại sao vật chất quyết định ý thức?",
    )

    assert result.startswith("**Trả lời trực tiếp:** Vật chất quyết định ý thức.")
    assert "\n- Vật chất là nguồn gốc của ý thức." in result
    assert "\n- Ý thức tác động trở lại vật chất" in result
    assert "**Kết luận:** Vật chất quyết định ý thức." not in result


def test_definition_formatter_joins_fragments_into_a_natural_paragraph() -> None:
    answer = (
        "**Định nghĩa:** Triết học là hệ thống tri thức lý luận chung nhất "
        "của con người về thế giới;\n\n"
        "**Đặc điểm chính:**\n"
        "- nghiên cứu những quy luật chung của tự nhiên, xã hội và tư duy.\n"
        "- về vị trí và vai trò của con người trong thế giới ấy."
    )

    result = format_grounded_answer(answer, "definition", "Triết học là gì?")

    assert result.startswith("**Định nghĩa:** Triết học là hệ thống")
    assert "**Đặc điểm chính:**" not in result
    assert "\n- " not in result
    assert "về vị trí và vai trò" in result


def test_definition_sentence_is_not_split_at_semicolon() -> None:
    from src.grounded_answer import split_answer_sentences

    answer = (
        "Triết học là hệ thống tri thức lý luận chung nhất về thế giới; "
        "đồng thời làm rõ vị trí của con người trong thế giới ấy."
    )

    assert split_answer_sentences(answer) == [answer]


def test_explicit_definition_is_extracted_across_adjacent_pages() -> None:
    contexts = [
        chunk(
            "chunk-1",
            'The manual gives this definition: "Cache invalidation is the process of removing stale',
            page=4,
        ),
        chunk(
            "chunk-2",
            'entries so that later reads load current data from the source." Additional notes follow.',
            page=5,
        ),
    ]

    result = extract_explicit_definition(
        "Cache invalidation được định nghĩa như thế nào?",
        contexts,
    )

    assert result is not None
    assert "removing stale entries" in result.answer
    assert result.answer.endswith('source.”')
    assert result.used_chunk_ids == ["chunk-1", "chunk-2"]


def test_explicit_definition_accepts_unquoted_textbook_summary_cue() -> None:
    result = extract_explicit_definition(
        "Triết học là gì?",
        [chunk(
            "definition",
            "Đã có nhiều cách định nghĩa khác nhau. Khái quát lại, có thể hiểu: "
            "Triết học là hệ thống tri thức lý luận chung nhất của con người về thế giới; "
            "về vị trí, vai trò của con người trong thế giới ấy.",
            page=4,
        )],
    )

    assert result is not None
    assert "hệ thống tri thức lý luận chung nhất" in result.answer
    assert result.used_chunk_ids == ["definition"]


def test_historical_origin_uses_the_complete_direct_source_statement() -> None:
    result = extract_historical_origin(
        "Triết học ra đời sớm nhất ở đâu?",
        [
            chunk(
                "distractor",
                "Triết học cổ điển Đức đạt đỉnh cao với Hêghđn vào thế kỷ XIX.",
                page=5,
            ),
            chunk(
                "origin",
                "Triết học ra đời ở cả phương Đông và phương Tây gần như cùng một "
                "thời gian, khoảng thế kỷ VIII đến VI trước Công nguyên, tại Trung Quốc, "
                "Ấn Độ và Hy Lạp.",
                page=2,
            ),
        ],
    )

    assert result is not None
    assert "phương Đông và phương Tây" in result.answer
    assert "Trung Quốc, Ấn Độ và Hy Lạp" in result.answer
    assert result.used_chunk_ids == ["origin"]


def test_explicit_definition_accepts_subject_sentence_after_definition_cue() -> None:
    result = extract_explicit_definition(
        "Vật chất theo quan điểm của Lênin là gì?",
        [chunk(
            "definition",
            "Như vậy, định nghĩa vật chất của V.I.Lênin bao gồm nội dung cơ bản sau: "
            "Vật chất là cái tồn tại khách quan bên ngoài ý thức và không phụ thuộc "
            "vào ý thức, bất kể con người đã nhận thức được hay chưa. "
            "Lênin đã cho phép xác định cái gì là vật chất trong lĩnh vực xã hội.",
            page=81,
        )],
    )

    assert result is not None
    assert "tồn tại khách quan" in result.answer
    assert result.used_chunk_ids == ["definition"]


def test_unrelated_quotation_is_not_used_as_a_definition() -> None:
    result = extract_explicit_definition(
        "Cache invalidation được định nghĩa như thế nào?",
        [
            chunk(
                "chunk-1",
                'The project history includes the slogan "ship early and learn quickly" from the team.',
            )
        ],
    )

    assert result is None


def test_explicit_definition_does_not_drop_requested_components() -> None:
    result = extract_explicit_definition(
        "He thong la gi va gom nhung mat nao?",
        [
            chunk(
                "definition",
                'The manual gives this definition: "A system is a set of related elements."',
                page=4,
            ),
            chunk(
                "components",
                "The system has two aspects: its elements and the relations between them.",
                page=5,
            ),
        ],
    )

    assert result is None


def test_explicit_definition_composes_attributed_quote_and_components() -> None:
    result = extract_explicit_definition(
        "Theo T\u00e1c gi\u1ea3 A, h\u1ec7 th\u1ed1ng l\u00e0 g\u00ec v\u00e0 "
        "g\u1ed3m nh\u1eefng m\u1eb7t n\u00e0o?",
        [
            chunk(
                "definition",
                'Theo T\u00e1c gi\u1ea3 A: "H\u1ec7 th\u1ed1ng l\u00e0 m\u1ed9t '
                't\u1eadp h\u1ee3p c\u00e1c ph\u1ea7n t\u1eed c\u00f3 li\u00ean h\u1ec7."',
                page=4,
            ),
            chunk(
                "components",
                "M\u1eb7t th\u1ee9 nh\u1ea5t: C\u1ea5u tr\u00fac c\u1ee7a c\u00e1c "
                "ph\u1ea7n t\u1eed. M\u1eb7t th\u1ee9 hai: Quan h\u1ec7 gi\u1eefa "
                "c\u00e1c ph\u1ea7n t\u1eed.",
                page=5,
            ),
        ],
    )

    assert result is not None
    assert "**Ph\u00e1t bi\u1ec3u:**" in result.answer
    assert "**M\u1eb7t th\u1ee9 nh\u1ea5t:**" in result.answer
    assert "**M\u1eb7t th\u1ee9 hai:**" in result.answer
    assert result.used_chunk_ids == ["definition", "components"]


def test_formatter_preserves_model_markdown_that_is_already_structured() -> None:
    answer = "- Ý thứ nhất.\n- Ý thứ hai."

    assert format_grounded_answer(answer, "list", "Hãy liệt kê") == answer


def test_procedure_formatter_uses_numbered_steps() -> None:
    answer = "Chuẩn bị dữ liệu. Kiểm tra dữ liệu. Chạy đánh giá."

    result = format_grounded_answer(answer, "procedure", "Quy trình gồm những bước nào?")

    assert result == "1. Chuẩn bị dữ liệu.\n2. Kiểm tra dữ liệu.\n3. Chạy đánh giá."


def test_context_limit_follows_answer_depth() -> None:
    topics = [
        "bản thể", "nhận thức", "đạo đức", "logic", "xã hội", "tự nhiên",
        "con người", "thực tiễn", "ý thức", "vật chất", "biện chứng",
        "phương pháp", "thế giới quan",
    ]
    contexts = [
        chunk(
            f"chunk-{index}",
            f"Khái niệm số {index} trình bày nội dung riêng về {topic} cùng các đặc điểm tiêu biểu.",
            page=index,
        )
        for index, topic in enumerate(topics, start=1)
    ]

    short = select_context_windows(
        "Hãy nêu các khái niệm",
        contexts,
        answer_profile="list",
        answer_depth="SHORT",
    )
    deep = select_context_windows(
        "Hãy trình bày chi tiết các khái niệm",
        contexts,
        answer_profile="list",
        answer_depth="DEEP",
    )

    assert len(short) == 5
    assert len(short) < len(deep) <= 12


def test_deep_answer_detects_missing_coverage_and_truncated_ending() -> None:
    answer = "- Học thuyết thứ nhất giải thích một nội dung quan trọng;"

    issues = answer_completeness_issues(
        answer,
        "list",
        "DEEP",
        evidence_count=5,
    )

    assert any("kết thúc giữa" in issue for issue in issues)
    assert any("ít nhất 3 ý" in issue for issue in issues)


def test_reasoning_requires_direct_answer_and_two_reasons_when_evidence_exists() -> None:
    answer = (
        "Vật chất quyết định ý thức vì vật chất có trước. "
        "Ý thức hình thành trong hoạt động thực tiễn của con người."
    )

    issues = answer_completeness_issues(
        answer,
        "reasoning",
        "STANDARD",
        evidence_count=5,
    )

    assert any("ít nhất 3 ý" in issue for issue in issues)


def test_formatter_does_not_drop_items_after_eighth_point() -> None:
    answer = " ".join(f"Ý thứ {index} có nội dung riêng." for index in range(1, 11))

    result = format_grounded_answer(answer, "list", "Hãy liệt kê đầy đủ")

    assert "- Ý thứ 10 có nội dung riêng." in result
    assert result.count("\n- ") == 9


def test_formatter_removes_repeated_markdown_points() -> None:
    answer = (
        "- **Âm Dương:** Giải thích sự biến hóa của vũ trụ.\n"
        "- **Ngũ hành:** Nêu năm yếu tố cơ bản.\n"
        "- **Âm Dương:** Giải thích sự biến hóa của vũ trụ."
    )

    result = format_grounded_answer(answer, "list", "Nêu các học thuyết")

    assert result.count("Âm Dương") == 1
    assert "Ngũ hành" in result


def test_doctrine_list_drops_background_and_recovers_trailing_named_item() -> None:
    answer = (
        "- **Thuyết Âm - Dương:** Âm Dương mô tả sự biến hóa của vũ trụ.\n"
        "- **Hoàn cảnh ra đời:** Triết học Trung Hoa xuất hiện trong xã hội cổ đại.\n"
        "- **Triết học Trung Hoa cổ, trung đại:** Bối cảnh chung n Ngũ hành: "
        "Ngũ hành là một khái niệm quan trọng trong triết học Trung Hoa."
    )

    result = format_grounded_answer(
        answer,
        "list",
        "Một số học thuyết tiêu biểu của triết học Trung Hoa cổ, trung đại",
    )

    assert "Hoàn cảnh ra đời" not in result
    assert "Bối cảnh chung" not in result
    assert "**Ngũ hành:**" in result
    assert "không có đủ bằng chứng" in result


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
