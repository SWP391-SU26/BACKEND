from __future__ import annotations

import csv
import hashlib
import json
import random
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


FINETUNED_REFUSAL_MESSAGE = "Tôi chưa tìm thấy thông tin này trong tài liệu đã được huấn luyện."
UUID_FILENAME_PREFIX = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}[_-]",
    re.IGNORECASE,
)


def normalize_source_name(value: str) -> str:
    if not value or not value.strip():
        return ""
    filename = Path(value.strip()).name
    return UUID_FILENAME_PREFIX.sub("", filename).casefold()


def build_finetuning_system_prompt(allowed_sources: list[str] | set[str] | tuple[str, ...]) -> str:
    sources = sorted({Path(source).name for source in allowed_sources if source and source.strip()})
    scope = ", ".join(sources) if sources else "tập tài liệu đã được huấn luyện"
    return (
        "Bạn là trợ lý học tập đã được fine-tune cho Triết học Mác - Lênin. "
        f"Chỉ trả lời kiến thức thuộc nguồn {scope}. "
        "Không trộn kiến thức giữa các tài liệu. "
        f"Nếu ngoài phạm vi, chỉ trả lời: {FINETUNED_REFUSAL_MESSAGE}"
    )


def training_source_names(paths: list[Path]) -> set[str]:
    """Return normalized source filenames that were actually used for fine-tuning."""
    sources: set[str] = set()
    for path in paths:
        if not path.exists():
            continue
        with path.open("r", encoding="utf-8") as handle:
            for raw_line in handle:
                if not raw_line.strip():
                    continue
                try:
                    item = json.loads(raw_line)
                except json.JSONDecodeError:
                    continue
                metadata = item.get("metadata") or {}
                source = normalize_source_name(str(metadata.get("source") or ""))
                if source and not bool(metadata.get("is_out_of_scope")):
                    sources.add(source)
    return sources


def selected_sources_are_trained(selected_filenames: list[str], trained_sources: set[str]) -> bool:
    selected = {
        normalize_source_name(filename)
        for filename in selected_filenames
        if filename and filename.strip()
    }
    normalized_trained = {
        normalize_source_name(filename)
        for filename in trained_sources
        if filename and filename.strip()
    }
    return bool(selected) and selected.issubset(normalized_trained)


@dataclass(frozen=True)
class FineTuningExample:
    messages: list[dict[str, str]]
    metadata: dict[str, Any]


def prepare_dataset(
    source_csv: Path,
    output_dir: Path,
    validation_ratio: float = 0.2,
    seed: int = 42,
    retain_all_knowledge: bool = False,
) -> dict[str, Any]:
    if validation_ratio < 0 or validation_ratio >= 1:
        raise ValueError("validation_ratio phải nằm trong khoảng [0, 1).")

    examples = load_qa_csv(source_csv)
    if not examples:
        raise ValueError("Không có cặp question/expected_answer hợp lệ trong CSV.")

    train_examples, validation_examples = split_examples(examples, validation_ratio, seed)
    if retain_all_knowledge and validation_examples:
        # Fine-tuning is the model's knowledge-retention stage: every approved fact
        # must be present in training. Validation still stays behaviorally distinct
        # by asking selected facts with an instruction/paraphrase wrapper.
        train_examples = list(examples)
        validation_examples = [
            build_validation_variant(example) for example in validation_examples
        ]

    output_dir.mkdir(parents=True, exist_ok=True)
    train_path = output_dir / "train.jsonl"
    validation_path = output_dir / "validation.jsonl"
    write_jsonl(train_path, train_examples)
    write_jsonl(validation_path, validation_examples)

    all_sources = sorted({
        normalize_source_name(str(example.metadata.get("source") or ""))
        for example in examples
        if not example.metadata.get("is_out_of_scope")
    } - {""})
    out_of_scope_examples = sum(bool(example.metadata.get("is_out_of_scope")) for example in examples)
    summary = {
        "source_csv": str(source_csv),
        "train_path": str(train_path),
        "validation_path": str(validation_path),
        "total_examples": len(examples),
        "train_examples": len(train_examples),
        "validation_examples": len(validation_examples),
        "validation_ratio": validation_ratio,
        "seed": seed,
        "retain_all_knowledge": retain_all_knowledge,
        "sources": all_sources,
        "source_count": len(all_sources),
        "in_scope_examples": len(examples) - out_of_scope_examples,
        "out_of_scope_examples": out_of_scope_examples,
        "dataset_fingerprint": dataset_fingerprint(examples),
        "quality": dataset_quality(examples, train_examples, validation_examples),
    }
    (output_dir / "dataset_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return summary


def build_validation_variant(example: FineTuningExample) -> FineTuningExample:
    messages = [dict(message) for message in example.messages]
    user_index = next(
        index for index, message in enumerate(messages) if message.get("role") == "user"
    )
    original_question = str(messages[user_index].get("content") or "").strip()
    messages[user_index]["content"] = (
        "Dựa đúng trên tài liệu đã học, hãy trả lời câu hỏi được diễn đạt lại sau đây: "
        f"{original_question}"
    )
    return FineTuningExample(messages=messages, metadata=dict(example.metadata))


def load_qa_csv(path: Path) -> list[FineTuningExample]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or not {"question", "expected_answer"}.issubset(reader.fieldnames):
            raise ValueError("CSV phải có cột question và expected_answer.")

        rows = list(reader)
        known_sources = sorted({
            Path((row.get("expected_source") or "").strip()).name
            for row in rows
            if (row.get("expected_source") or "").strip() and not parse_bool(row.get("is_out_of_scope") or "")
        })
        examples: list[FineTuningExample] = []
        for index, row in enumerate(rows, start=2):
            question = (row.get("question") or "").strip()
            answer = (row.get("expected_answer") or "").strip()
            is_out_of_scope = parse_bool(row.get("is_out_of_scope") or "")
            source = (row.get("expected_source") or "").strip()
            if not question or (not answer and not is_out_of_scope):
                continue
            allowed_sources = known_sources if is_out_of_scope else ([source] if source else known_sources)
            if is_out_of_scope:
                answer = FINETUNED_REFUSAL_MESSAGE
            examples.append(
                FineTuningExample(
                    messages=[
                        {"role": "system", "content": build_finetuning_system_prompt(allowed_sources)},
                        {"role": "user", "content": question},
                        {"role": "assistant", "content": answer},
                    ],
                    metadata={
                        "source": source,
                        "allowed_sources": allowed_sources,
                        "page": (row.get("expected_page") or "").strip(),
                        "subject": (row.get("subject") or "").strip(),
                        "category": (row.get("category") or "general").strip(),
                        "is_out_of_scope": is_out_of_scope,
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
    metadata = item.get("metadata")
    if not isinstance(metadata, dict):
        raise ValueError("metadata phải là object.")
    if not bool(metadata.get("is_out_of_scope")) and not str(metadata.get("source") or "").strip():
        raise ValueError("Ví dụ trong phạm vi phải có metadata.source.")


def write_jsonl(path: Path, examples: list[FineTuningExample]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for example in examples:
            handle.write(json.dumps(asdict(example), ensure_ascii=False) + "\n")


def parse_bool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "y", "co", "có"}


def split_examples(
    examples: list[FineTuningExample], validation_ratio: float, seed: int
) -> tuple[list[FineTuningExample], list[FineTuningExample]]:
    if validation_ratio == 0 or len(examples) < 2:
        return list(examples), []
    groups: dict[bool, list[FineTuningExample]] = {False: [], True: []}
    for example in examples:
        groups[bool(example.metadata.get("is_out_of_scope"))].append(example)
    rng = random.Random(seed)
    train: list[FineTuningExample] = []
    validation: list[FineTuningExample] = []
    for group in groups.values():
        rng.shuffle(group)
        if not group:
            continue
        count = round(len(group) * validation_ratio)
        if len(group) > 1:
            count = max(1, min(count, len(group) - 1))
        else:
            count = 0
        validation.extend(group[:count])
        train.extend(group[count:])
    rng.shuffle(train)
    rng.shuffle(validation)
    return train, validation


def dataset_fingerprint(examples: list[FineTuningExample]) -> str:
    payload = "\n".join(
        json.dumps(asdict(example), ensure_ascii=False, sort_keys=True) for example in examples
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def dataset_quality(
    examples: list[FineTuningExample],
    train_examples: list[FineTuningExample],
    validation_examples: list[FineTuningExample],
) -> dict[str, Any]:
    normalized_questions = [
        re.sub(r"\W+", " ", next(m["content"] for m in item.messages if m["role"] == "user").casefold()).strip()
        for item in examples
    ]
    duplicate_count = len(normalized_questions) - len(set(normalized_questions))
    refusal_count = sum(bool(item.metadata.get("is_out_of_scope")) for item in examples)
    source_count = len({
        normalize_source_name(str(item.metadata.get("source") or ""))
        for item in examples
        if not item.metadata.get("is_out_of_scope")
    } - {""})
    issues: list[str] = []
    if len(examples) < 100:
        issues.append("Cần ít nhất 100 ví dụ; nên có 200-500 ví dụ chất lượng cho mỗi môn.")
    if refusal_count < max(10, round(len(examples) * 0.1)):
        issues.append("Thiếu ví dụ ngoài phạm vi để model học cách từ chối.")
    if not validation_examples:
        issues.append("Thiếu tập validation độc lập.")
    if duplicate_count:
        issues.append(f"Có {duplicate_count} câu hỏi trùng sau khi chuẩn hóa.")
    return {
        "passed": not issues,
        "issues": issues,
        "source_count": source_count,
        "refusal_examples": refusal_count,
        "duplicate_questions": duplicate_count,
        "train_examples": len(train_examples),
        "validation_examples": len(validation_examples),
    }


def training_questions_by_source(paths: list[Path]) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for path in paths:
        if not path.exists():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            if not raw_line.strip():
                continue
            try:
                item = json.loads(raw_line)
            except json.JSONDecodeError:
                continue
            metadata = item.get("metadata") or {}
            if bool(metadata.get("is_out_of_scope")):
                continue
            source = normalize_source_name(str(metadata.get("source") or ""))
            messages = item.get("messages") or []
            question = next(
                (str(message.get("content") or "").strip() for message in messages if message.get("role") == "user"),
                "",
            )
            if source and question:
                result.setdefault(source, []).append(question)
    return result


def refusal_questions_by_source(paths: list[Path]) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for path in paths:
        if not path.exists():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            if not raw_line.strip():
                continue
            try:
                item = json.loads(raw_line)
            except json.JSONDecodeError:
                continue
            metadata = item.get("metadata") or {}
            if not bool(metadata.get("is_out_of_scope")):
                continue
            messages = item.get("messages") or []
            question = next(
                (str(message.get("content") or "").strip() for message in messages if message.get("role") == "user"),
                "",
            )
            for source_value in metadata.get("allowed_sources") or []:
                source = normalize_source_name(str(source_value))
                if source and question:
                    result.setdefault(source, []).append(question)
    return result


def is_refusal_answer(answer: str) -> bool:
    normalized = re.sub(r"\s+", " ", (answer or "").casefold()).strip(" .!\n\t")
    expected = FINETUNED_REFUSAL_MESSAGE.casefold().strip(" .!")
    refusal_markers = (
        "không có trong tài liệu",
        "chưa tìm thấy thông tin",
        "chưa tìm được thông tin",
        "không tìm thấy thông tin",
        "không tìm được thông tin",
        "chưa tìm ra thông tin",
        "không tìm ra thông tin",
    )
    return (
        normalized == expected
        or any(marker in normalized for marker in refusal_markers)
        or (
            ("tôi chưa tìm" in normalized or "tôi không tìm" in normalized)
            and not any(marker in normalized for marker in ("nhưng", "tuy nhiên", "theo tôi"))
        )
    )
