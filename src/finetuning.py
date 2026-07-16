from __future__ import annotations

import csv
import json
import random
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


SYSTEM_PROMPT = (
    "Bạn là trợ lý học tập. Hãy trả lời câu hỏi bằng tiếng Việt rõ ràng, "
    "chính xác và không bịa thông tin."
)


@dataclass(frozen=True)
class FineTuningExample:
    messages: list[dict[str, str]]
    metadata: dict[str, Any]


def prepare_dataset(
    source_csv: Path,
    output_dir: Path,
    validation_ratio: float = 0.2,
    seed: int = 42,
) -> dict[str, Any]:
    if validation_ratio < 0 or validation_ratio >= 1:
        raise ValueError("validation_ratio phải nằm trong khoảng [0, 1).")

    examples = load_qa_csv(source_csv)
    if not examples:
        raise ValueError("Không có cặp question/expected_answer hợp lệ trong CSV.")

    random.Random(seed).shuffle(examples)
    validation_count = round(len(examples) * validation_ratio)
    if validation_ratio > 0 and len(examples) > 1:
        validation_count = max(1, min(validation_count, len(examples) - 1))
    train_examples = examples[validation_count:]
    validation_examples = examples[:validation_count]

    output_dir.mkdir(parents=True, exist_ok=True)
    train_path = output_dir / "train.jsonl"
    validation_path = output_dir / "validation.jsonl"
    write_jsonl(train_path, train_examples)
    write_jsonl(validation_path, validation_examples)

    summary = {
        "source_csv": str(source_csv),
        "train_path": str(train_path),
        "validation_path": str(validation_path),
        "total_examples": len(examples),
        "train_examples": len(train_examples),
        "validation_examples": len(validation_examples),
        "validation_ratio": validation_ratio,
        "seed": seed,
    }
    (output_dir / "dataset_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return summary


def load_qa_csv(path: Path) -> list[FineTuningExample]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or not {"question", "expected_answer"}.issubset(reader.fieldnames):
            raise ValueError("CSV phải có cột question và expected_answer.")

        examples: list[FineTuningExample] = []
        for index, row in enumerate(reader, start=2):
            question = (row.get("question") or "").strip()
            answer = (row.get("expected_answer") or "").strip()
            is_out_of_scope = parse_bool(row.get("is_out_of_scope") or "")
            if not question or not answer or is_out_of_scope:
                continue
            examples.append(
                FineTuningExample(
                    messages=[
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": question},
                        {"role": "assistant", "content": answer},
                    ],
                    metadata={
                        "source": (row.get("expected_source") or "").strip(),
                        "page": (row.get("expected_page") or "").strip(),
                        "subject": (row.get("subject") or "").strip(),
                        "category": (row.get("category") or "general").strip(),
                        "csv_line": index,
                    },
                )
            )
    return examples


def validate_jsonl(path: Path) -> dict[str, Any]:
    errors: list[str] = []
    count = 0
    if not path.exists():
        return {"path": str(path), "valid": False, "examples": 0, "errors": ["File không tồn tại."]}

    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            if not raw_line.strip():
                continue
            try:
                item = json.loads(raw_line)
                validate_example(item)
                count += 1
            except (json.JSONDecodeError, ValueError) as exc:
                errors.append(f"Dòng {line_number}: {exc}")
    return {"path": str(path), "valid": not errors and count > 0, "examples": count, "errors": errors}


def validate_example(item: dict[str, Any]) -> None:
    messages = item.get("messages")
    if not isinstance(messages, list) or len(messages) < 2:
        raise ValueError("messages phải là danh sách có ít nhất 2 phần tử.")
    roles = [message.get("role") for message in messages if isinstance(message, dict)]
    if "user" not in roles or "assistant" not in roles:
        raise ValueError("messages phải có role user và assistant.")
    for message in messages:
        if not isinstance(message, dict) or not str(message.get("content", "")).strip():
            raise ValueError("Mỗi message phải có content.")


def write_jsonl(path: Path, examples: list[FineTuningExample]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for example in examples:
            handle.write(json.dumps(asdict(example), ensure_ascii=False) + "\n")


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "y", "co", "có"}
