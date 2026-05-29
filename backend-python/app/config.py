"""
Load environment variables từ .env
Python AI Engine chỉ cần config cho AI layer — không có auth, không có Azure SQL
"""

from pydantic_settings import BaseSettings
from typing import Optional
from loguru import logger
from functools import lru_cache


class Settings(BaseSettings):
    # Qdrant Vector DB
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333

    # LLM
    openai_api_key: Optional[str] = None
    gemini_api_key: Optional[str] = None
    llm_model: str = "gpt-4o-mini"

    # Embedding
    embedding_model_default: str = "BAAI/bge-m3"

    # Fine-tuned model (HuggingFace Inference API)
    finetuned_model_endpoint: Optional[str] = None
    huggingface_api_key: Optional[str] = None

    # Server
    python_ai_port: int = 8001
    log_level: str = "INFO"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


@lru_cache
def get_settings() -> Settings:
    """Singleton settings — cache sau lần đầu load"""
    return Settings()


def log_config_status() -> None:
    """Log trạng thái config khi khởi động"""
    s = get_settings()
    logger.info("=== AI Engine Config ===")
    logger.info(f"Qdrant: {s.qdrant_host}:{s.qdrant_port}")
    logger.info(f"LLM: {s.llm_model}")
    logger.info(f"Embedding default: {s.embedding_model_default}")
    logger.info(f"OpenAI key: {'set' if s.openai_api_key else 'NOT SET'}")
    logger.info(f"Gemini key: {'set' if s.gemini_api_key else 'NOT SET'}")
    logger.info(f"HuggingFace endpoint: {'set' if s.finetuned_model_endpoint else 'NOT SET'}")
    logger.info("========================")
