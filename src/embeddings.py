from __future__ import annotations

import hashlib
import math
from abc import ABC, abstractmethod
from pathlib import Path

from .config import AppSettings
from .text_utils import tokenize


class EmbeddingProvider(ABC):
    name: str
    model: str

    @abstractmethod
    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        raise NotImplementedError

    def embed_query(self, text: str) -> list[float]:
        return self.embed_texts([text])[0]


class HashingEmbeddingProvider(EmbeddingProvider):
    """Small deterministic embedding fallback for offline demos.

    This is not as strong as bge-m3/OpenAI embeddings, but it makes the whole
    chatbot runnable without network access, API keys, or GPU.
    """

    name = "local-hash"
    model = "local-hash-v1"

    def __init__(self, dimensions: int = 384) -> None:
        self.dimensions = dimensions

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(text) for text in texts]

    def _embed(self, text: str) -> list[float]:
        vector = [0.0] * self.dimensions
        tokens = tokenize(text)
        features = tokens[:]
        features.extend(f"{left}_{right}" for left, right in zip(tokens, tokens[1:]))
        for feature in features:
            digest = hashlib.blake2b(feature.encode("utf-8"), digest_size=8).digest()
            value = int.from_bytes(digest, "big")
            index = value % self.dimensions
            sign = 1.0 if (value >> 1) & 1 else -1.0
            vector[index] += sign
        norm = math.sqrt(sum(item * item for item in vector)) or 1.0
        return [item / norm for item in vector]


class SentenceTransformerEmbeddingProvider(EmbeddingProvider):
    def __init__(
        self,
        model: str,
        cache_folder: str | None = None,
        device: str = "cpu",
    ) -> None:
        from sentence_transformers import SentenceTransformer
        import torch

        self.name = "sentence-transformers"
        self.model = model
        requested_device = device.strip().lower()
        self.device = (
            "cuda"
            if requested_device == "cuda" and torch.cuda.is_available()
            else "cpu"
        )
        local_model = find_cached_snapshot(Path(cache_folder), model, "modules.json") if cache_folder else None
        try:
            self._model = SentenceTransformer(
                str(local_model or model),
                device=self.device,
                cache_folder=cache_folder,
                local_files_only=True,
            )
        except Exception:
            self._model = SentenceTransformer(
                model,
                device=self.device,
                cache_folder=cache_folder,
            )
        if self.device == "cuda":
            self._model.half()

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        vectors = self._model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
        return [vector.tolist() for vector in vectors]


class FastEmbedEmbeddingProvider(EmbeddingProvider):
    """CPU-friendly ONNX embeddings for fully offline runtime after first download."""

    def __init__(self, model: str, cache_folder: str | None = None) -> None:
        from fastembed import TextEmbedding

        self.name = "fastembed-onnx"
        self.model = model
        self._model = TextEmbedding(model_name=model, cache_dir=cache_folder, threads=4)

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        normalized: list[list[float]] = []
        for vector in self._model.embed(texts, batch_size=32):
            values = vector.tolist()
            norm = math.sqrt(sum(value * value for value in values)) or 1.0
            normalized.append([value / norm for value in values])
        return normalized


class OpenAIEmbeddingProvider(EmbeddingProvider):
    def __init__(self, model: str, api_key: str) -> None:
        from openai import OpenAI

        self.name = "openai"
        self.model = model
        self._client = OpenAI(api_key=api_key)

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        response = self._client.embeddings.create(model=self.model, input=texts)
        return [item.embedding for item in response.data]


def get_embedding_provider(settings: AppSettings) -> EmbeddingProvider:
    provider = settings.embedding_provider.lower().strip()
    if provider == "openai":
        if not settings.openai_api_key:
            raise RuntimeError("OPENAI_API_KEY is required when EMBEDDING_PROVIDER=openai.")
        return OpenAIEmbeddingProvider(settings.embedding_model, settings.openai_api_key)
    if provider in {"sentence-transformers", "sentence_transformers", "hf"}:
        return SentenceTransformerEmbeddingProvider(
            settings.embedding_model,
            cache_folder=str(settings.model_cache_dir),
            device=settings.embedding_device,
        )
    if provider in {"fastembed", "fastembed-onnx", "onnx"}:
        return FastEmbedEmbeddingProvider(
            settings.embedding_model,
            cache_folder=str(settings.model_cache_dir),
        )
    return HashingEmbeddingProvider()


def find_cached_snapshot(cache_folder: Path, model: str, required_file: str) -> Path | None:
    model_dir = cache_folder / f"models--{model.replace('/', '--')}" / "snapshots"
    if not model_dir.exists():
        return None
    for snapshot in sorted(model_dir.iterdir(), key=lambda path: path.stat().st_mtime, reverse=True):
        if snapshot.is_dir() and (snapshot / required_file).exists():
            return snapshot
    return None
