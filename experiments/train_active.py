from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from experiments.train_lora import required_packages_for, run_training
from src.config import ensure_data_dirs, load_settings
from src.finetuning import prepare_dataset, validate_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare a CSV dataset and train a LoRA/QLoRA adapter with portable paths."
    )
    parser.add_argument("source", help="CSV ground-truth file. Absolute path or path relative to BACKEND.")
    parser.add_argument("--run-name", default=None, help="Name for dataset/model output, e.g. triet301 or math101.")
    parser.add_argument("--base-config", default="experiments/lora_config.json")
    parser.add_argument("--dataset-dir", default=None)
    parser.add_argument("--output-dir", default=None)
    parser.add_argument("--validation-ratio", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--retain-all-knowledge",
        action="store_true",
        help="Train on every approved fact and validate on instruction/paraphrase variants.",
    )
    parser.add_argument("--prepare-only", action="store_true", help="Only create train/validation JSONL.")
    parser.add_argument("--dry-run", action="store_true", help="Prepare data, validate config, then stop.")
    args = parser.parse_args()

    source_path = resolve_input_path(args.source)
    run_name = slugify(args.run_name or source_path.stem)
    dataset_dir = resolve_root_path(args.dataset_dir or f"data/finetuning/{run_name}")
    output_dir = resolve_root_path(args.output_dir or f"models/{run_name}")
    base_config_path = resolve_root_path(args.base_config)
    generated_config_path = ROOT / "experiments" / "generated" / f"lora_config_{run_name}.json"

    settings = load_settings()
    ensure_data_dirs(settings)
    prepare_summary = prepare_dataset(
        source_csv=source_path,
        output_dir=dataset_dir,
        validation_ratio=args.validation_ratio,
        seed=args.seed,
        retain_all_knowledge=args.retain_all_knowledge,
    )

    config = json.loads(base_config_path.read_text(encoding="utf-8"))
    config["train_file"] = root_relative(dataset_dir / "train.jsonl")
    config["validation_file"] = root_relative(dataset_dir / "validation.jsonl")
    config["output_dir"] = root_relative(output_dir)
    generated_config_path.parent.mkdir(parents=True, exist_ok=True)
    generated_config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")

    train_validation = validate_jsonl(dataset_dir / "train.jsonl")
    validation_validation = validate_jsonl(dataset_dir / "validation.jsonl")
    missing = [name for name in required_packages_for(config) if importlib.util.find_spec(name) is None]
    summary = {
        "run_name": run_name,
        "source_csv": str(source_path),
        "dataset": prepare_summary,
        "config_path": str(generated_config_path),
        "config": config,
        "train_dataset": train_validation,
        "validation_dataset": validation_validation,
        "missing_packages": missing,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))

    if args.prepare_only or args.dry_run:
        return
    if missing:
        raise RuntimeError("Missing fine-tuning packages: " + ", ".join(missing))
    if not train_validation["valid"]:
        raise RuntimeError("Train dataset is not valid.")

    run_training(config)


def resolve_input_path(value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path.resolve()
    return (ROOT / path).resolve()


def resolve_root_path(value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path.resolve()
    return (ROOT / path).resolve()


def root_relative(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return str(path.resolve())


def slugify(value: str) -> str:
    slug = re.sub(r"[^a-zA-Z0-9._-]+", "-", value.strip().lower()).strip("-._")
    return slug or "fine-tuning-run"


if __name__ == "__main__":
    main()
