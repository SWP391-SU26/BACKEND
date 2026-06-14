from __future__ import annotations

import argparse
import sys
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from src.config import ensure_data_dirs, load_settings
from src.embeddings import get_embedding_provider
from src.rag_pipeline import RAGPipeline
from src.storage import SQLiteStore


DOCUMENTS = [
    ("SWT4.pdf", "Software Testing", "Test Design Techniques"),
    ("UseCaseDetailedSpecification.pdf", "Software Testing", "Use-case Specification"),
    ("chu han.pdf", "Chữ Hán", "Lesson 3"),
    ("speaking test bai 6+7.pdf", "Chữ Hán", "Speaking bài 6-7"),
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Index project documents with an embedding model.")
    parser.add_argument("--embedding-provider", default=None)
    parser.add_argument("--embedding-model", default=None)
    args = parser.parse_args()

    settings = load_settings()
    settings = replace(
        settings,
        embedding_provider=args.embedding_provider or settings.embedding_provider,
        embedding_model=args.embedding_model or settings.embedding_model,
    )
    ensure_data_dirs(settings)
    provider = get_embedding_provider(settings)
    pipeline = RAGPipeline(settings, SQLiteStore(settings.db_path), provider)

    for filename, subject, chapter in DOCUMENTS:
        path = settings.raw_dir / filename
        if not path.exists():
            print(f"SKIP {filename}: file not found")
            continue
        result = pipeline.ingest_file(path, subject, chapter)
        print(f"INDEXED {filename}: {result.num_pages} pages, {result.num_chunks} chunks")


if __name__ == "__main__":
    main()
