import json
from pathlib import Path

from experiments.train_lora import build_training_manifest


def write_rows(path: Path, count: int, refusal_count: int) -> None:
    rows = []
    for index in range(count):
        rows.append({
            "messages": [
                {"role": "user", "content": f"Question {index}"},
                {"role": "assistant", "content": f"Answer {index}"},
            ],
            "metadata": {"is_out_of_scope": index < refusal_count},
        })
    path.write_text(
        "\n".join(json.dumps(row) for row in rows) + "\n",
        encoding="utf-8",
    )


def test_manifest_stays_unverified_until_behavioral_gate_runs(tmp_path: Path) -> None:
    train = tmp_path / "train.jsonl"
    validation = tmp_path / "validation.jsonl"
    dataset_manifest = tmp_path / "dataset_manifest.json"
    output = tmp_path / "adapter-v1"
    write_rows(train, 100, 10)
    write_rows(validation, 20, 2)
    dataset_manifest.write_text(
        json.dumps({
            "dataset_version": "test-v1",
            "source": {"sha256": "pdf-checksum"},
        }),
        encoding="utf-8",
    )
    manifest = build_training_manifest(
        {
            "model_name": "Qwen/Qwen2.5-0.5B-Instruct",
            "train_file": str(train),
            "validation_file": str(validation),
            "dataset_manifest": str(dataset_manifest),
            "output_dir": str(output),
            "min_training_examples": 100,
            "min_refusal_examples": 10,
            "max_eval_loss": 3.0,
        },
        {
            "eval_loss": 2.0,
            "eval_num_tokens": 1000,
            "train_loss": 1.5,
        },
    )

    assert manifest["dataset_version"] == "test-v1"
    assert manifest["pdf_sha256"] == "pdf-checksum"
    assert manifest["quality_gate"]["checks"]["behavioral_smoke_test"] is False
    assert manifest["quality_gate"]["passed"] is False
