from types import SimpleNamespace
import sys

import torch

from src.embeddings import SentenceTransformerEmbeddingProvider


class _FakeSentenceTransformer:
    def __init__(self, *_args, **_kwargs) -> None:
        self.half_called = False

    def half(self):
        self.half_called = True
        return self


def test_cuda_embedding_model_uses_half_precision(monkeypatch) -> None:
    monkeypatch.setattr(torch.cuda, "is_available", lambda: True)
    monkeypatch.setitem(
        sys.modules,
        "sentence_transformers",
        SimpleNamespace(SentenceTransformer=_FakeSentenceTransformer),
    )

    provider = SentenceTransformerEmbeddingProvider("test-model", device="cuda")

    assert provider.device == "cuda"
    assert provider._model.half_called is True


def test_cpu_embedding_model_keeps_default_precision(monkeypatch) -> None:
    monkeypatch.setattr(torch.cuda, "is_available", lambda: True)
    monkeypatch.setitem(
        sys.modules,
        "sentence_transformers",
        SimpleNamespace(SentenceTransformer=_FakeSentenceTransformer),
    )

    provider = SentenceTransformerEmbeddingProvider("test-model", device="cpu")

    assert provider.device == "cpu"
    assert provider._model.half_called is False
