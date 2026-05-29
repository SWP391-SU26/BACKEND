"""
Configuration module for FastAPI AI Engine
Loads environment variables and provides config object
"""

from pydantic_settings import BaseSettings
from typing import Optional
import logging

logger = logging.getLogger(__name__)


class Settings(BaseSettings):
    """Application settings loaded from .env"""

    # Azure SQL
    azure_sql_server: str = "localhost"
    azure_sql_database: str = "VietnameseCourseQA20DB"
    azure_sql_user: str = "sa"
    azure_sql_password: str = "password"

    # Qdrant Vector DB
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333

    # Azure Blob Storage
    azure_storage_connection_string: Optional[str] = None

    # LLM API Keys
    openai_api_key: Optional[str] = None
    gemini_api_key: Optional[str] = None
    llm_model: str = "gpt-4o-mini"

    # Embedding Models
    embedding_model_default: str = "BAAI/bge-m3"

    # Fine-tuned Model
    finetuned_model_endpoint: Optional[str] = None
    huggingface_api_key: Optional[str] = None

    # Auth & JWT
    jwt_secret_key: str = "your-secret-key-change-in-production"
    jwt_expiration_ms: int = 3600000  # 1 hour

    # Service Ports
    python_ai_port: int = 8001
    java_be_port: int = 8080

    # Logging
    log_level: str = "INFO"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# Load settings
settings = Settings()


def get_settings() -> Settings:
    """
    Dependency injection for FastAPI
    Returns the settings object
    """
    return settings


def log_config_status() -> None:
    """Log which services are available/configured"""
    logger.info("=== AI Engine Configuration ===")
    logger.info(f"Qdrant: {settings.qdrant_host}:{settings.qdrant_port}")
    logger.info(f"LLM Model: {settings.llm_model}")
    logger.info(f"Embedding Model: {settings.embedding_model_default}")
    logger.info(f"OpenAI available: {bool(settings.openai_api_key)}")
    logger.info(f"Gemini available: {bool(settings.gemini_api_key)}")
    logger.info(f"Fine-tuned model available: {bool(settings.finetuned_model_endpoint)}")
    logger.info("================================")
 
