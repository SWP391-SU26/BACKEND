from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.config import ensure_data_dirs, load_settings
from src.finetuning import prepare_dataset


def main() -> None:
    parser = argparse.ArgumentParser(description="Chuẩn bị dataset JSONL cho fine-tuning.")
    parser.add_argument("--source", default=str(ROOT / "data" / "test_set.csv"))
    parser.add_argument("--output-dir", default=None)
    parser.add_argument("--validation-ratio", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--retain-all-knowledge", action="store_true")
    args = parser.parse_args()

    settings = load_settings()
    ensure_data_dirs(settings)
    output_dir = Path(args.output_dir).resolve() if args.output_dir else settings.finetuning_dir
    result = prepare_dataset(
        source_csv=Path(args.source).resolve(),
        output_dir=output_dir,
        validation_ratio=args.validation_ratio,
        seed=args.seed,
        retain_all_knowledge=args.retain_all_knowledge,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
