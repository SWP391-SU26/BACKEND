from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.embeddings import find_cached_snapshot


def read_questions(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def main() -> None:
    parser = argparse.ArgumentParser(description="Warn about near-duplicate questions across research splits.")
    parser.add_argument("--dataset-dir", default="data/research/triethoc-v1")
    parser.add_argument("--threshold", type=float, default=0.90)
    args = parser.parse_args()

    from sentence_transformers import SentenceTransformer

    dataset_dir = (ROOT / args.dataset_dir).resolve()
    cache_dir = ROOT / "data/models_cache/hub"
    model_name = "BAAI/bge-m3"
    cached = find_cached_snapshot(cache_dir, model_name, "modules.json")
    model = SentenceTransformer(
        str(cached or model_name),
        cache_folder=str(cache_dir),
        local_files_only=cached is not None,
        device="cpu",
    )
    splits = {
        name: read_questions(dataset_dir / f"{name}.csv")
        for name in ("train", "validation", "test")
    }
    pairs = []
    for left_name, right_name in (("train", "validation"), ("train", "test"), ("validation", "test")):
        left = splits[left_name]
        right = splits[right_name]
        left_vectors = model.encode(
            [row["question"] for row in left], normalize_embeddings=True, show_progress_bar=False
        )
        right_vectors = model.encode(
            [row["question"] for row in right], normalize_embeddings=True, show_progress_bar=False
        )
        similarities = np.asarray(left_vectors) @ np.asarray(right_vectors).T
        for left_index, right_index in np.argwhere(similarities >= args.threshold):
            pairs.append({
                "left_split": left_name,
                "right_split": right_name,
                "left_question": left[int(left_index)]["question"],
                "right_question": right[int(right_index)]["question"],
                "cosine": round(float(similarities[left_index, right_index]), 6),
            })
    report = {
        "embedding_model": model_name,
        "threshold": args.threshold,
        "warning_count": len(pairs),
        "warnings": pairs,
    }
    output = dataset_dir / "semantic_leakage_report.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"report": str(output), **report}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
