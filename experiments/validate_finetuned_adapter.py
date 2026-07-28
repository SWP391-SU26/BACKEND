from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.finetuning import is_refusal_answer
from src.shared_qwen import SharedQwenRuntime


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Behavioral quality gate for an offline LoRA adapter.")
    parser.add_argument("--adapter", required=True)
    parser.add_argument(
        "--manifest",
        default=None,
        help="Manifest to read and update. Defaults to the adapter directory or its parent for checkpoints.",
    )
    parser.add_argument("--validation", default="data/research/triethoc-v1/validation.jsonl")
    parser.add_argument(
        "--base-model",
        default=None,
        help="Override the base model. By default it is read from training_manifest.json.",
    )
    parser.add_argument("--model-cache", default="data/models_cache/hub")
    parser.add_argument("--min-answer-f1", type=float, default=0.35)
    parser.add_argument("--min-refusal-accuracy", type=float, default=0.80)
    parser.add_argument("--max-new-tokens", type=int, default=192)
    parser.add_argument(
        "--report",
        default="output/flow5/finetuned_quality_gate_qwen1.5b.json",
    )
    args = parser.parse_args()

    adapter = resolve(args.adapter)
    validation_path = resolve(args.validation)
    manifest_path = resolve(args.manifest) if args.manifest else find_manifest(adapter)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    base_model = args.base_model or manifest.get("base_model")
    if not base_model:
        raise SystemExit("training_manifest.json does not declare base_model; pass --base-model explicitly.")
    rows = [json.loads(line) for line in validation_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    generator = SharedQwenRuntime(
        base_model,
        adapter,
        resolve(args.model_cache),
        max_input_tokens=1536,
        max_new_tokens=args.max_new_tokens,
    )

    answer_scores: list[float] = []
    refusal_results: list[bool] = []
    examples: list[dict] = []
    for index, row in enumerate(rows, start=1):
        metadata = row.get("metadata") or {}
        messages = row.get("messages") or []
        question = next(message["content"] for message in messages if message["role"] == "user")
        expected = next(message["content"] for message in messages if message["role"] == "assistant")
        allowed_sources = metadata.get("allowed_sources") or [metadata.get("source")]
        actual = generator.generate_without_context(
            question,
            allowed_sources,
            allow_unverified=True,
            max_input_tokens=1536,
            max_new_tokens=args.max_new_tokens,
        )
        out_of_scope = bool(metadata.get("is_out_of_scope"))
        if out_of_scope:
            refusal_results.append(is_refusal_answer(actual))
        else:
            answer_scores.append(token_f1(expected, actual))
        examples.append({
            "question": question,
            "expected": expected,
            "actual": actual,
            "is_out_of_scope": out_of_scope,
        })
        if index % 5 == 0 or index == len(rows):
            print(f"Validated {index}/{len(rows)} examples.", file=sys.stderr, flush=True)

    answer_f1 = sum(answer_scores) / max(1, len(answer_scores))
    refusal_accuracy = sum(refusal_results) / max(1, len(refusal_results))
    passed = answer_f1 >= args.min_answer_f1 and refusal_accuracy >= args.min_refusal_accuracy
    manifest.setdefault("evaluation_metrics", {}).update({
        "behavioral_answer_token_f1": round(answer_f1, 4),
        "behavioral_refusal_accuracy": round(refusal_accuracy, 4),
        "behavioral_examples": len(rows),
    })
    gate = manifest.setdefault("quality_gate", {})
    checks = gate.setdefault("checks", {})
    checks["behavioral_smoke_test"] = passed
    gate["passed"] = all(bool(value) for value in checks.values())
    gate["behavioral_thresholds"] = {
        "min_answer_token_f1": args.min_answer_f1,
        "min_refusal_accuracy": args.min_refusal_accuracy,
    }
    if not gate["passed"]:
        gate["rejection_reason"] = "Behavioral benchmark chưa đạt ngưỡng chất lượng production."
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    report = {
        "adapter": str(adapter),
        "passed": gate["passed"],
        "answer_token_f1": round(answer_f1, 4),
        "refusal_accuracy": round(refusal_accuracy, 4),
        "examples": examples,
    }
    report_path = resolve(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps({
        "report": str(report_path),
        "passed": report["passed"],
        "answer_token_f1": report["answer_token_f1"],
        "refusal_accuracy": report["refusal_accuracy"],
        "examples": len(examples),
    }, ensure_ascii=False, indent=2))
    if not gate["passed"]:
        raise SystemExit(2)


def resolve(value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (ROOT / path).resolve()


def find_manifest(adapter: Path) -> Path:
    candidates = (adapter / "training_manifest.json", adapter.parent / "training_manifest.json")
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise SystemExit(f"Could not find training_manifest.json for adapter {adapter}.")


def token_f1(expected: str, actual: str) -> float:
    expected_tokens = re.findall(r"[^\W_]+", expected.casefold(), flags=re.UNICODE)
    actual_tokens = re.findall(r"[^\W_]+", actual.casefold(), flags=re.UNICODE)
    if not expected_tokens or not actual_tokens:
        return 0.0
    overlap = sum((Counter(expected_tokens) & Counter(actual_tokens)).values())
    precision = overlap / len(actual_tokens)
    recall = overlap / len(expected_tokens)
    return 2 * precision * recall / max(1e-9, precision + recall)


if __name__ == "__main__":
    main()
