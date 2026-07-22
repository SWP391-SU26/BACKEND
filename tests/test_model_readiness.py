from pathlib import Path
from types import SimpleNamespace

import pytest

from src.config import AppSettings, BASE_DIR, load_settings
from src.rag_pipeline import RAGPipeline


class DummyStore:
    pass


class DummyEmbedding:
    model = "deterministic-test"


def test_adapter_path_is_absolute_and_points_to_committed_adapter(monkeypatch) -> None:
    monkeypatch.setenv("LORA_ADAPTER_DIR", "stale/missing/adapter")
    settings = load_settings()
    assert settings.lora_adapter_dir.is_absolute()
    assert settings.lora_adapter_dir == (BASE_DIR / "models" / "qwen-rag-lora").resolve()


def test_model_readiness_reports_missing_adapter(tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path / "missing")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    status = pipeline.generation_status()
    assert status["adapter_ready"] is False
    assert status["inference_ready"] is False


def test_strict_rag_does_not_use_extractive_fallback(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path / "missing", generation_provider="extractive")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    monkeypatch.setattr(pipeline, "_get_local_generator", lambda: None)
    pipeline.local_generator_error = "adapter missing"
    context = SimpleNamespace(content="RAG combines retrieval and generation.")
    with pytest.raises(RuntimeError, match="adapter missing"):
        pipeline._generate_answer("What is RAG?", [context], [], strict=True)


def test_strict_rag_uses_deterministic_generator(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path, generation_provider="extractive")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    generator = SimpleNamespace(generate=lambda question, contexts, history: "deterministic answer")
    monkeypatch.setattr(pipeline, "_get_local_generator", lambda: generator)
    assert pipeline._generate_answer("Question", [SimpleNamespace()], [], strict=True) == "deterministic answer"


def test_finetuned_generation_uses_real_generator_path(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path)
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    generator = SimpleNamespace(generate_without_context=lambda question: f"fine:{question}")
    monkeypatch.setattr(pipeline, "_get_local_generator", lambda: generator)
    assert pipeline.generate_without_retrieval("Question") == "fine:Question"
