from __future__ import annotations

from dataclasses import dataclass

from .document_loader import DocumentPage
from .text_utils import normalize_text


@dataclass(frozen=True)
class TextChunk:
    text: str
    page: int | None
    chunk_index: int


import re

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
        paragraphs = re.split(r'\n\s*\n', page.text)
        current_words = []
        
        for paragraph in paragraphs:
            para_words = normalize_text(paragraph).split()
            if not para_words:
                continue
                
            if not current_words or len(current_words) + len(para_words) <= chunk_size:
                current_words.extend(para_words)
            else:
                text = " ".join(current_words).strip()
                if text:
                    chunks.append(TextChunk(text=text, page=page.page, chunk_index=chunk_index))
                    chunk_index += 1
                
                # Start new chunk with overlap
                overlap_words = current_words[-overlap:] if overlap > 0 else []
                current_words = overlap_words + para_words
                
                # If paragraph itself is larger than chunk_size, split it further
                while len(current_words) > chunk_size:
                    end = chunk_size
                    text = " ".join(current_words[:end]).strip()
                    if text:
                        chunks.append(TextChunk(text=text, page=page.page, chunk_index=chunk_index))
                        chunk_index += 1
                    if overlap > 0:
                        current_words = current_words[end - overlap:]
                    else:
                        current_words = current_words[end:]

        if current_words:
            text = " ".join(current_words).strip()
            if text:
                chunks.append(TextChunk(text=text, page=page.page, chunk_index=chunk_index))
                chunk_index += 1

    return chunks

