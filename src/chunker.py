from __future__ import annotations

from dataclasses import dataclass

from .document_loader import DocumentPage
from .text_utils import normalize_text


@dataclass(frozen=True)
class TextChunk:
    text: str
    page: int | None
    chunk_index: int


def chunk_pages(
    pages: list[DocumentPage],
    chunk_size: int = 700,
    overlap: int = 120,
) -> list[TextChunk]:
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")
    if overlap < 0 or overlap >= chunk_size:
        raise ValueError("overlap must be >= 0 and smaller than chunk_size")

    chunks: list[TextChunk] = []
    chunk_index = 0
    for page in pages:
        words = normalize_text(page.text).split()
        if not words:
            continue

        start = 0
        while start < len(words):
            end = min(start + chunk_size, len(words))
            text = " ".join(words[start:end]).strip()
            if text:
                chunks.append(TextChunk(text=text, page=page.page, chunk_index=chunk_index))
                chunk_index += 1
            if end >= len(words):
                break
            start = max(0, end - overlap)

    return chunks

