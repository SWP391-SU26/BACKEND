import csv
import shutil
import uuid
from pathlib import Path

from src.finetuning import prepare_dataset, validate_jsonl


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

        assert summary["total_examples"] == 1
        assert validation["valid"] is True
        assert validation["examples"] == 1
    finally:
        shutil.rmtree(root, ignore_errors=True)
