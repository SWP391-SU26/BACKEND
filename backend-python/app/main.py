"""
FastAPI entrypoint — AI Engine
Expose 5 internal endpoints cho Java gọi
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from loguru import logger
from app.config import log_config_status

# Import routers (implement sau)
from api.index_document import router as index_router
from api.chat import router as chat_router
from api.chat_finetuned import router as finetuned_router
from api.evaluate import router as evaluate_router
from api.benchmark import router as benchmark_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup và shutdown events"""
    logger.info("Starting AI Engine...")
    log_config_status()
    # TODO: kiểm tra kết nối Qdrant khi startup
    yield
    logger.info("Shutting down AI Engine...")


app = FastAPI(
    title="SU26SWP09 — AI Engine",
    version="1.0.0",
    description="Internal AI service: RAG + Fine-tuned pipeline. Chỉ Java gọi, FE không gọi trực tiếp.",
    lifespan=lifespan
)

# CORS — chỉ cho phép Java BE gọi
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],  # Java port
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)

# Đăng ký 5 routers
app.include_router(index_router, prefix="/ai", tags=["Document Indexing"])
app.include_router(chat_router, prefix="/ai", tags=["RAG Chat"])
app.include_router(finetuned_router, prefix="/ai", tags=["Fine-tuned Chat"])
app.include_router(evaluate_router, prefix="/ai", tags=["Evaluator"])
app.include_router(benchmark_router, prefix="/ai", tags=["Benchmark"])


@app.get("/health")
async def health():
    """Health check — Java gọi để kiểm tra Python còn sống không"""
    return {"status": "ok", "service": "ai-engine", "version": "1.0.0"}
 
