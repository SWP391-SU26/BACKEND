from src.grounded_answer import ensure_grounded_answer, select_context_windows
from src.storage import RetrievedChunk


def evidence(chunk_id: str, content: str) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=chunk_id,
        document_id="doc-1",
        filename="course.pdf",
        subject="Philosophy",
        chapter="Chinese philosophy",
        page=19,
        content=content,
        score=0.8,
    )


def test_named_list_item_must_appear_in_retrieved_evidence() -> None:
    contexts = [
        evidence(
            "chunk-1",
            "Thuyet Am Duong va Ngu hanh giai thich su bien dich cua vu tru.",
        ),
        evidence(
            "chunk-2",
            "Nho gia lay cac van de chinh tri va dao duc lam cot loi.",
        ),
    ]
    generated = (
        "- Thuyet Am Duong va Ngu hanh: Giai thich su bien dich cua vu tru.\n"
        "- Hoai nghi luan thoi phuc hung: Phe phan cac quan diem ton giao.\n"
        "- Nho gia: Lay cac van de chinh tri va dao duc lam cot loi."
    )

    result = ensure_grounded_answer(
        "Neu cac hoc thuyet Am Duong Ngu hanh va Nho gia",
        generated,
        contexts,
        answer_profile="list",
    )

    assert "Thuyet Am Duong va Ngu hanh" in result.answer
    assert "Nho gia" in result.answer
    assert "Hoai nghi luan" not in result.answer


def test_deep_list_keeps_section_evidence_beyond_keyword_anchor() -> None:
    context = evidence(
        "chunk-3",
        (
            "Triet hoc Trung Hoa co nhieu hoc thuyet tieu bieu. "
            "Day la noi dung dan nhap cua muc. "
            "Cac van de xa hoi duoc nghien cuu sau sac. "
            "Con nguoi la trung tam cua nhieu quan diem. "
            "Nhieu khai niem da phat trien qua cac giai doan. "
            "Nho gia lay Nhan Nghia lam goc. "
            "Dao gia de cao Dao va tu tuong Vo vi."
        ),
    )

    selected = select_context_windows(
        "Cac hoc thuyet tieu bieu cua triet hoc Trung Hoa",
        [context],
        answer_profile="list",
        answer_depth="DEEP",
    )

    assert "Nho gia" in selected[0].content
    assert "Dao gia" in selected[0].content
