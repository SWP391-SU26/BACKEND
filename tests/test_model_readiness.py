import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from src.config import AppSettings, BASE_DIR, load_settings
from src.rag_pipeline import RAGPipeline


class DummyStore:
    pass


class DummyEmbedding:
    model = "deterministic-test"


def test_adapter_path_is_absolute_and_points_to_configured_candidate(monkeypatch) -> None:
    monkeypatch.setenv("LORA_ADAPTER_DIR", "stale/missing/adapter")
    settings = load_settings()
    assert settings.lora_adapter_dir.is_absolute()
    assert settings.lora_adapter_dir == (BASE_DIR / "stale" / "missing" / "adapter").resolve()


def test_model_readiness_reports_missing_adapter(tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path / "missing")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    status = pipeline.generation_status()
    assert status["adapter_ready"] is False
    assert status["inference_ready"] is False


def test_model_readiness_rejects_adapter_built_for_another_base_model(tmp_path: Path) -> None:
    adapter = tmp_path / "adapter"
    adapter.mkdir()
    for filename in ("adapter_config.json", "adapter_model.safetensors", "tokenizer_config.json"):
        (adapter / filename).write_text("{}", encoding="utf-8")
    (adapter / "training_manifest.json").write_text(
        json.dumps({
            "base_model": "Qwen/Qwen2.5-1.5B-Instruct",
            "quality_gate": {"passed": True, "checks": {"behavioral_smoke_test": True}},
        }),
        encoding="utf-8",
    )

    settings = AppSettings(
        lora_adapter_dir=adapter,
        local_base_model="Qwen/Qwen2.5-0.5B-Instruct",
    )
    status = RAGPipeline(settings, DummyStore(), DummyEmbedding()).generation_status()

    assert status["adapter_ready"] is True
    assert status["base_model_matches"] is False
    assert status["configured_ready"] is False


def test_explicit_override_allows_a_matching_unverified_adapter(tmp_path: Path) -> None:
    adapter = tmp_path / "adapter"
    adapter.mkdir()
    for filename in ("adapter_config.json", "adapter_model.safetensors", "tokenizer_config.json"):
        (adapter / filename).write_text("{}", encoding="utf-8")
    (adapter / "training_manifest.json").write_text(
        json.dumps({
            "base_model": "Qwen/Qwen2.5-1.5B-Instruct",
            "quality_gate": {"passed": False, "checks": {"behavioral_smoke_test": False}},
        }),
        encoding="utf-8",
    )

    settings = AppSettings(
        lora_adapter_dir=adapter,
        local_base_model="Qwen/Qwen2.5-1.5B-Instruct",
        allow_unverified_finetuned=True,
    )
    status = RAGPipeline(settings, DummyStore(), DummyEmbedding()).generation_status()

    assert status["base_model_matches"] is True
    assert status["quality_gate_overridden"] is True
    assert status["configured_ready"] is True


def test_strict_extractive_rag_does_not_require_lora(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path / "missing", generation_provider="extractive")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    monkeypatch.setattr(
        pipeline,
        "_get_local_generator",
        lambda: pytest.fail("extractive RAG must not initialize the fine-tuned model"),
    )
    monkeypatch.setattr(pipeline, "_generate_extractive_answer", lambda *_args: "grounded answer")
    context = SimpleNamespace(content="RAG combines retrieval and generation.")
    assert pipeline._generate_answer("What is RAG?", [context], [], strict=True) == "grounded answer"


def test_strict_rag_uses_deterministic_generator(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path, generation_provider="local")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    generator = SimpleNamespace(generate=lambda question, contexts: "deterministic answer")
    monkeypatch.setattr(pipeline, "_get_local_generator", lambda: generator)
    assert pipeline._generate_answer("Question", [SimpleNamespace()], [], strict=True) == "deterministic answer"


def test_base_rag_never_uses_lora_or_openai(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(
        lora_adapter_dir=tmp_path / "missing",
        generation_provider="openai",
        openai_api_key="not-used",
    )
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    generator = SimpleNamespace(generate=lambda question, contexts, **_kwargs: "base grounded answer")
    monkeypatch.setattr(pipeline, "_get_base_generator", lambda: generator)
    monkeypatch.setattr(
        pipeline,
        "_get_local_generator",
        lambda: pytest.fail("BASE_RAG must not initialize the LoRA adapter"),
    )

    result = pipeline.generate_base_rag_answer("Question", [SimpleNamespace()])

    assert result.answer == "base grounded answer"
    assert result.provider_used == "local-base"
    assert result.generation_mode == "BASE_RAG"
    assert result.adapter_version is None


def test_single_japanese_character_is_valid_list_item(tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path, generation_provider="extractive")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())

    assert pipeline._is_weak_list_item("き") is False
    assert pipeline._is_weak_list_item("ことば Từ vựng") is True


def test_japanese_section_question_is_understood(tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path, generation_provider="extractive")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())

    assert pipeline._is_list_question("文法をまとめてください") is True
    assert pipeline._is_summary_question("文法をまとめてください") is True
    assert pipeline._list_answer_label("語彙をまとめてください") == "danh sách từ vựng tìm thấy"


def test_general_vietnamese_question_forms_are_understood(tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path, generation_provider="extractive")
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())

    assert pipeline._question_form("Triết học được hiểu như thế nào?") == "definition"
    assert pipeline._question_form("So sánh biện chứng và siêu hình") == "comparison"
    assert pipeline._question_form("Vì sao thế giới quan quan trọng?") == "reasoning"
    assert pipeline._is_list_question("Quy trình này gồm những bước nào?") is True


def test_finetuned_generation_uses_real_generator_path(monkeypatch, tmp_path: Path) -> None:
    settings = AppSettings(lora_adapter_dir=tmp_path)
    pipeline = RAGPipeline(settings, DummyStore(), DummyEmbedding())
    generator = SimpleNamespace(
        generate_without_context=lambda question, allowed_sources, **_kwargs: f"fine:{question}:{allowed_sources[0]}"
    )
    monkeypatch.setattr(pipeline, "_get_local_generator", lambda: generator)
    assert pipeline.generate_without_retrieval("Question", ["course.pdf"]) == "fine:Question:course.pdf"
