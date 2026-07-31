import csv
import json
import shutil
import uuid
from pathlib import Path

from src.finetuning import (
    FINETUNED_REFUSAL_MESSAGE,
    build_finetuning_system_prompt,
    is_refusal_answer,
    prepare_dataset,
    selected_sources_are_trained,
    training_source_names,
    validate_jsonl,
)
from src.finetuned_scope import FineTunedScopeGuard


def test_prepare_finetuning_dataset() -> None:
    root = Path.cwd() / "tests" / "_tmp" / str(uuid.uuid4())
    root.mkdir(parents=True, exist_ok=True)
    try:
        source = root / "qa.csv"
        with source.open("w", encoding="utf-8", newline="") as handle:
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
                    "expected_answer": "RAG ket hop truy hoi va sinh cau tra loi.",
                    "expected_source": "demo.txt",
                    "is_out_of_scope": "false",
                    "category": "definition",
                }
            )
            writer.writerow(
                {
                    "question": "Cau ngoai tai lieu?",
                    "expected_answer": "",
                    "expected_source": "",
                    "is_out_of_scope": "true",
                    "category": "out_of_scope",
                }
            )

        summary = prepare_dataset(source, root / "output", validation_ratio=0)
        validation = validate_jsonl(Path(summary["train_path"]))

        assert summary["total_examples"] == 2
        assert summary["out_of_scope_examples"] == 1
        assert validation["valid"] is True
        assert validation["examples"] == 2
        rows = [json.loads(line) for line in Path(summary["train_path"]).read_text(encoding="utf-8").splitlines()]
        refusal = next(row for row in rows if row["metadata"]["is_out_of_scope"])
        assert refusal["messages"][-1]["content"] == FINETUNED_REFUSAL_MESSAGE
    finally:
        shutil.rmtree(root, ignore_errors=True)


def test_training_source_names_only_returns_sources_used_by_the_model(tmp_path: Path) -> None:
    train_path = tmp_path / "train.jsonl"
    train_path.write_text(
        "\n".join([
            json.dumps({"metadata": {"source": "docs/Philosophy.pdf"}}),
            json.dumps({"metadata": {"source": "PHILOSOPHY.pdf"}}),
            json.dumps({"metadata": {}}),
        ]),
        encoding="utf-8",
    )

    assert training_source_names([train_path, tmp_path / "missing.jsonl"]) == {"philosophy.pdf"}
    assert selected_sources_are_trained(["Philosophy.pdf"], {"philosophy.pdf"}) is True
    assert selected_sources_are_trained(["Japanese.pdf"], {"philosophy.pdf"}) is False
    assert selected_sources_are_trained([], {"philosophy.pdf"}) is False


def test_selected_source_matches_training_source_after_uuid_storage_prefix() -> None:
    stored_name = "75770b9b-cdbf-4038-90e2-f25e1f4426fe_triethocmaclenin.pdf"

    assert selected_sources_are_trained(
        ["triethocmaclenin.pdf"],
        {stored_name},
    ) is True
    assert selected_sources_are_trained(
        [stored_name],
        {"triethocmaclenin.pdf"},
    ) is True


def test_finetuning_prompt_is_conditioned_on_selected_document() -> None:
    prompt = build_finetuning_system_prompt(["Japanese.pdf"])

    assert "Japanese.pdf" in prompt
    assert "Không trộn kiến thức" in prompt
    assert FINETUNED_REFUSAL_MESSAGE in prompt


def test_refusal_detection_accepts_equivalent_local_model_wording() -> None:
    assert is_refusal_answer("Tôi chưa tìm được thông tin về nội dung này trong tài liệu.") is True
    assert is_refusal_answer("Không tìm thấy thông tin phù hợp trong tài liệu đã học.") is True
    assert is_refusal_answer("Định luật Ohm mô tả quan hệ giữa U, I và R.") is False


def test_scope_guard_uses_only_questions_from_selected_document(tmp_path: Path) -> None:
    path = tmp_path / "train.jsonl"
    rows = [
        {
            "messages": [
                {"role": "user", "content": "Thế giới quan là gì?"},
                {"role": "assistant", "content": "Khái niệm triết học."},
            ],
            "metadata": {"source": "philosophy.pdf", "is_out_of_scope": False},
        },
        {
            "messages": [
                {"role": "user", "content": "日本 đọc như thế nào?"},
                {"role": "assistant", "content": "にほん"},
            ],
            "metadata": {"source": "japanese.pdf", "is_out_of_scope": False},
        },
        {
            "messages": [
                {"role": "user", "content": "Định luật Ohm là gì?"},
                {"role": "assistant", "content": FINETUNED_REFUSAL_MESSAGE},
            ],
            "metadata": {
                "source": "",
                "allowed_sources": ["philosophy.pdf"],
                "is_out_of_scope": True,
            },
        },
    ]
    path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows), encoding="utf-8")

    class Embedding:
        def vector(self, text: str) -> list[float]:
            return [1.0, 0.0] if "thế giới quan" in text.casefold() else [0.0, 1.0]

        def embed_query(self, text: str) -> list[float]:
            return self.vector(text)

        def embed_texts(self, texts: list[str]) -> list[list[float]]:
            return [self.vector(text) for text in texts]

    guard = FineTunedScopeGuard([path], Embedding(), min_similarity=0.8)
    assert guard.decide("Giải thích thế giới quan", ["philosophy.pdf"]).allowed is True
    assert guard.decide("Giải thích thế giới quan", ["japanese.pdf"]).allowed is False
    assert guard.decide("Định luật Ohm là gì?", ["philosophy.pdf"]).allowed is False
