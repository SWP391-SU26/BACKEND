from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data"
RAW_DIR = DATA_DIR / "raw"
PROCESSED_DIR = DATA_DIR / "processed"
DB_DIR = DATA_DIR / "db"
REPORTS_DIR = BASE_DIR / "reports"
FINETUNING_DIR = DATA_DIR / "research" / "triethoc-v1"
MODEL_CACHE_DIR = DATA_DIR / "models_cache"
LORA_ADAPTER_DIR = BASE_DIR / "models" / "qwen2.5-1.5b-triethoc-lora-v1"


@dataclass(frozen=True)
class AppSettings:
    raw_dir: Path = RAW_DIR
    processed_dir: Path = PROCESSED_DIR
    db_path: Path = DB_DIR / "chatbot.sqlite3"
    reports_dir: Path = REPORTS_DIR
    finetuning_dir: Path = FINETUNING_DIR
    model_cache_dir: Path = MODEL_CACHE_DIR / "hub"
    lora_adapter_dir: Path = LORA_ADAPTER_DIR
    top_k: int = 5
    chunk_size: int = 500
    chunk_overlap: int = 50
    min_retrieval_score: float = 0.2
    semantic_weight: float = 0.4
    embedding_provider: str = "sentence-transformers"
    embedding_model: str = "BAAI/bge-m3"
    embedding_device: str = "cpu"
    openai_api_key: str | None = None
    openai_chat_model: str = "gpt-4o-mini"
    generation_provider: str = "auto"
    local_base_model: str = "Qwen/Qwen2.5-1.5B-Instruct"
    local_max_input_tokens: int = 2048
    local_max_new_tokens: int = 256
    benchmark_batch_size: int = 1
    benchmark_max_new_tokens: int = 192
    benchmark_max_input_tokens: int = 1536
    dataset_version: str = "triethoc-v1"
    prompt_version: str = "triethoc-grounded-v1"
    finetuned_scope_min_similarity: float = 0.60
    allow_unverified_finetuned: bool = False


def load_settings() -> AppSettings:
    """Load settings from environment variables, keeping demo-friendly defaults."""
    load_dotenv(BASE_DIR / ".env")
    adapter_value = Path(os.getenv("LORA_ADAPTER_DIR", LORA_ADAPTER_DIR))
    if not adapter_value.is_absolute():
        adapter_value = BASE_DIR / adapter_value
    return AppSettings(
        raw_dir=Path(os.getenv("RAW_DIR", RAW_DIR)),
        processed_dir=Path(os.getenv("PROCESSED_DIR", PROCESSED_DIR)),
        db_path=Path(os.getenv("APP_DB_PATH", DB_DIR / "chatbot.sqlite3")),
        reports_dir=Path(os.getenv("REPORTS_DIR", REPORTS_DIR)),
        finetuning_dir=Path(os.getenv("FINETUNING_DIR", FINETUNING_DIR)),
        model_cache_dir=Path(os.getenv("MODEL_CACHE_DIR", MODEL_CACHE_DIR / "hub")),
        lora_adapter_dir=adapter_value.resolve(),
        top_k=int(os.getenv("TOP_K", "5")),
        chunk_size=int(os.getenv("CHUNK_SIZE", "500")),
        chunk_overlap=int(os.getenv("CHUNK_OVERLAP", "50")),
        min_retrieval_score=float(os.getenv("MIN_RETRIEVAL_SCORE", "0.2")),
        semantic_weight=float(os.getenv("SEMANTIC_WEIGHT", "0.4")),
        embedding_provider=os.getenv("EMBEDDING_PROVIDER", "sentence-transformers"),
        embedding_model=os.getenv("EMBEDDING_MODEL", "BAAI/bge-m3"),
        embedding_device=os.getenv("EMBEDDING_DEVICE", "cpu").strip().lower(),
        openai_api_key=os.getenv("OPENAI_API_KEY") or None,
        openai_chat_model=os.getenv("OPENAI_CHAT_MODEL", "gpt-4o-mini"),
        generation_provider=os.getenv("GENERATION_PROVIDER", "auto"),
        local_base_model=os.getenv("LOCAL_BASE_MODEL", "Qwen/Qwen2.5-1.5B-Instruct"),
        local_max_input_tokens=max(256, int(os.getenv("LOCAL_MAX_INPUT_TOKENS", "2048"))),
        local_max_new_tokens=max(1, int(os.getenv("LOCAL_MAX_NEW_TOKENS", "256"))),
        benchmark_batch_size=max(1, int(os.getenv("BENCHMARK_BATCH_SIZE", "1"))),
        benchmark_max_new_tokens=max(1, int(os.getenv("BENCHMARK_MAX_NEW_TOKENS", "192"))),
        benchmark_max_input_tokens=max(256, int(os.getenv("BENCHMARK_MAX_INPUT_TOKENS", "1536"))),
        dataset_version=os.getenv("RESEARCH_DATASET_VERSION", "triethoc-v1"),
        prompt_version=os.getenv("RESEARCH_PROMPT_VERSION", "triethoc-grounded-v1"),
        finetuned_scope_min_similarity=float(os.getenv("FINETUNED_SCOPE_MIN_SIMILARITY", "0.60")),
        allow_unverified_finetuned=os.getenv("FINETUNING_ALLOW_UNVERIFIED", "false").strip().lower()
        in {"1", "true", "yes", "on"},
    )


def ensure_data_dirs(settings: AppSettings) -> None:
    for path in [
        settings.raw_dir,
        settings.processed_dir,
        settings.db_path.parent,
        settings.reports_dir,
        settings.finetuning_dir,
        settings.model_cache_dir,
    ]:
        path.mkdir(parents=True, exist_ok=True)


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value
