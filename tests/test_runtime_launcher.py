from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_run_api_does_not_override_model_or_benchmark_settings() -> None:
    launcher = (ROOT / "run_api.bat").read_text(encoding="utf-8").casefold()

    assert "models\\qwen-rag-lora" not in launcher
    assert "set \"lora_adapter_dir=" not in launcher
    assert "set \"benchmark_batch_size=" not in launcher
    assert "set \"benchmark_max_new_tokens=" not in launcher
    assert "set \"benchmark_max_input_tokens=" not in launcher


def test_env_selects_the_qwen_15b_adapter() -> None:
    env_text = (ROOT / ".env").read_text(encoding="utf-8")

    assert "LOCAL_BASE_MODEL=Qwen/Qwen2.5-1.5B-Instruct" in env_text
    assert "LORA_ADAPTER_DIR=models/qwen2.5-1.5b-triethoc-lora-v1" in env_text
