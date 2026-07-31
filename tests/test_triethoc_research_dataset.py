from __future__ import annotations

import csv
import hashlib
import json
import re
from pathlib import Path

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "data" / "research" / "triethoc-v1"


def test_research_dataset_counts_and_manifest_hashes() -> None:
    manifest = json.loads(
        (DATASET / "dataset_manifest.json").read_text(encoding="utf-8")
    )
    assert manifest["counts"] == {
        "train": 250,
        "train_in_scope": 225,
        "train_out_of_scope": 25,
        "validation": 50,
        "validation_in_scope": 45,
        "validation_out_of_scope": 5,
        "test": 50,
        "robustness": 10,
    }
    assert len(manifest["chapters"]) == 14
    assert manifest["source"]["sha256"] == (
        "6e69a2f7294df03d22e94f0c748f407f22231321b8cea1dd661b1f2c9e36e171"
    )
    assert manifest["leakage_policy"]["reviewed_test_concepts_excluded"] == 7
    for metadata in manifest["files"].values():
        path = ROOT / metadata["path"]
        assert path.exists()
        assert sha256(path) == metadata["sha256"]


def test_semantic_leakage_review_has_no_open_warnings() -> None:
    report = json.loads(
        (DATASET / "semantic_leakage_report.json").read_text(encoding="utf-8")
    )
    assert report["embedding_model"] == "BAAI/bge-m3"
    assert report["threshold"] == 0.9
    assert report["warning_count"] == 0
    assert report["warnings"] == []


def test_locked_test_does_not_leak_verbatim_into_training() -> None:
    train = load_csv(DATASET / "train.csv")
    validation = load_csv(DATASET / "validation.csv")
    test = load_csv(DATASET / "test.csv")
    training_questions = {
        normalize(row["question"]) for row in train + validation
    }
    training_answers = {
        normalize(row["expected_answer"])
        for row in train + validation
        if row["is_out_of_scope"].casefold() != "true"
    }
    assert not training_questions & {normalize(row["question"]) for row in test}
    assert not training_answers & {
        normalize(row["expected_answer"]) for row in test
    }


def test_all_grounded_rows_have_page_evidence_in_pdf() -> None:
    reader = PdfReader(str(ROOT / "data" / "corpus" / "triethoc_mac_lenin.pdf"))
    assert len(reader.pages) == 214
    pages = [(page.extract_text() or "") for page in reader.pages]
    for filename in ("train.csv", "validation.csv", "test.csv"):
        for row in load_csv(DATASET / filename):
            if row["is_out_of_scope"].casefold() == "true":
                continue
            page = int(row["expected_page"])
            assert row["evidence_quote"]
            assert row["evidence_quote"] in pages[page - 1]


def test_generated_training_answers_are_complete_and_self_contained() -> None:
    rejected_prefixes = (
        "do đó ",
        "từ đó ",
        "như vậy ",
        "vì vậy ",
        "tuy nhiên ",
        "song ",
        "nhưng ",
        "và ",
        "hay ",
        "điều này ",
        "điều đó ",
    )
    for filename in ("train.csv", "validation.csv"):
        for row in load_csv(DATASET / filename):
            if row["is_out_of_scope"].casefold() == "true":
                continue
            answer = row["expected_answer"].strip()
            assert answer.endswith((".", "?", "!", ";"))
            assert answer.count("(") == answer.count(")")
            assert not answer.casefold().startswith(rejected_prefixes)


def load_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def normalize(value: str) -> str:
    return " ".join(
        re.findall(r"[^\W_]+", value.casefold(), flags=re.UNICODE)
    )


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
