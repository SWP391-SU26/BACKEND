"""
Wrapper cho 4 embedding models
Tất cả return normalized L2 vectors
Cache model instance theo model_name (singleton per process)
"""

from typing import Callable
from loguru import logger
import numpy as np


# Model cache — load 1 lần, tái dụng mãi
_model_cache: dict = {}


def _get_bge_m3(texts: list[str]) -> list[list[float]]:
    """BAAI/bge-m3 — recommended primary model"""
    from sentence_transformers import SentenceTransformer
    if "bge-m3" not in _model_cache:
        logger.info("Loading BAAI/bge-m3...")
        _model_cache["bge-m3"] = SentenceTransformer("BAAI/bge-m3")
    model = _model_cache["bge-m3"]
    vectors = model.encode(texts, batch_size=32, normalize_embeddings=True)
    return vectors.tolist()


def _get_e5_base(texts: list[str], is_query: bool = False) -> list[list[float]]:
    """multilingual-e5-base — cần prefix 'query:' hoặc 'passage:'"""
    from sentence_transformers import SentenceTransformer
    if "e5-base" not in _model_cache:
        logger.info("Loading multilingual-e5-base...")
        _model_cache["e5-base"] = SentenceTransformer("intfloat/multilingual-e5-base")
    model = _model_cache["e5-base"]
    prefix = "query: " if is_query else "passage: "
    prefixed = [prefix + t for t in texts]
    vectors = model.encode(prefixed, normalize_embeddings=True)
    return vectors.tolist()


async def _get_openai(texts: list[str]) -> list[list[float]]:
    """text-embedding-3-small — OpenAI API, retry 3 lần"""
    from openai import AsyncOpenAI
    from app.config import get_settings
    import asyncio
    client = AsyncOpenAI(api_key=get_settings().openai_api_key)
    for attempt in range(3):
        try:
            response = await client.embeddings.create(
                model="text-embedding-3-small",
                input=texts,
                timeout=30
            )
            vectors = [item.embedding for item in response.data]
            # Normalize L2
            norms = [np.sqrt(sum(v**2 for v in vec)) for vec in vectors]
            return [[x / n for x, n in zip(v, [norm]*len(v))] for v, norm in zip(vectors, norms)]
        except Exception as e:
            if attempt == 2:
                logger.error(f"OpenAI embedding failed after 3 retries: {e}")
                raise
            await asyncio.sleep(2 ** attempt)


def _get_phobert(texts: list[str]) -> list[list[float]]:
    """PhoBERT — cần mean pooling thủ công"""
    import torch
    from transformers import AutoTokenizer, AutoModel
    if "phobert" not in _model_cache:
        logger.info("Loading PhoBERT...")
        tokenizer = AutoTokenizer.from_pretrained("vinai/phobert-base")
        model = AutoModel.from_pretrained("vinai/phobert-base")
        model.eval()
        _model_cache["phobert"] = (tokenizer, model)
    tokenizer, model = _model_cache["phobert"]

    encoded = tokenizer(texts, padding=True, truncation=True, max_length=256, return_tensors="pt")
    with torch.no_grad():
        output = model(**encoded)
    # Mean pooling
    attention_mask = encoded["attention_mask"]
    token_embeddings = output.last_hidden_state
    input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    vectors = torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(input_mask_expanded.sum(1), min=1e-9)
    # Normalize L2
    vectors = torch.nn.functional.normalize(vectors, p=2, dim=1)
    return vectors.tolist()


async def embed_texts(texts: list[str], model_name: str, is_query: bool = False) -> list[list[float]]:
    """
    Entry point chính — embed list of texts
    model_name: "bge-m3" | "multilingual-e5-base" | "text-embedding-3-small" | "phobert"
    is_query: True nếu embed câu hỏi (ảnh hưởng e5 prefix)
    """
    logger.debug(f"Embedding {len(texts)} texts with {model_name}")
    if model_name == "bge-m3":
        return _get_bge_m3(texts)
    elif model_name == "multilingual-e5-base":
        return _get_e5_base(texts, is_query=is_query)
    elif model_name == "text-embedding-3-small":
        return await _get_openai(texts)
    elif model_name == "phobert":
        return _get_phobert(texts)
    else:
        raise ValueError(f"Unknown embedding model: {model_name}")


async def embed_query(query: str, model_name: str) -> list[float]:
    """Embed 1 câu hỏi — shorthand cho single query"""
    vectors = await embed_texts([query], model_name, is_query=True)
    return vectors[0]

