"""
Qdrant client wrapper
Hỗ trợ: upsert chunks, search by vector, delete by document_id
"""

from qdrant_client import AsyncQdrantClient
from qdrant_client.models import (
    VectorParams, Distance, PointStruct,
    Filter, FieldCondition, MatchValue
)
from loguru import logger
from app.config import get_settings
import uuid


def _get_client() -> AsyncQdrantClient:
    """Singleton Qdrant client"""
    s = get_settings()
    return AsyncQdrantClient(host=s.qdrant_host, port=s.qdrant_port)


async def ensure_collection(collection_name: str, vector_size: int) -> None:
    """Tạo collection nếu chưa tồn tại"""
    client = _get_client()
    collections = await client.get_collections()
    names = [c.name for c in collections.collections]
    if collection_name not in names:
        await client.create_collection(
            collection_name=collection_name,
            vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE)
        )
        logger.info(f"Created Qdrant collection: {collection_name}")


async def upsert_chunks(
    collection_name: str,
    chunks: list[dict],
    vectors: list[list[float]],
    document_id: str
) -> list[str]:
    """
    Lưu chunks + vectors vào Qdrant
    chunks: list of {chunk_index, content, source_page, token_count}
    Trả về list point_ids
    """
    client = _get_client()
    await ensure_collection(collection_name, len(vectors[0]))

    points = []
    point_ids = []
    for chunk, vector in zip(chunks, vectors):
        point_id = str(uuid.uuid4())
        point_ids.append(point_id)
        points.append(PointStruct(
            id=point_id,
            vector=vector,
            payload={
                "chunk_index": chunk["chunk_index"],
                "content": chunk["content"],
                "source_page": chunk["source_page"],
                "token_count": chunk["token_count"],
                "document_id": document_id
            }
        ))

    await client.upsert(collection_name=collection_name, points=points)
    logger.info(f"Upserted {len(points)} chunks to {collection_name}")
    return point_ids


async def search_similar(
    collection_name: str,
    query_vector: list[float],
    top_k: int = 5,
    score_threshold: float = 0.72
) -> list[dict]:
    """
    Tìm top_k chunks giống query_vector nhất
    Trả về list {chunk_id, content, document_id, source_page, similarity_score}
    """
    client = _get_client()
    results = await client.search(
        collection_name=collection_name,
        query_vector=query_vector,
        limit=top_k,
        score_threshold=score_threshold,
        with_payload=True
    )
    return [
        {
            "chunk_id": str(r.id),
            "content": r.payload["content"],
            "document_id": r.payload["document_id"],
            "source_page": r.payload["source_page"],
            "similarity_score": r.score
        }
        for r in results
    ]


async def delete_by_document(collection_name: str, document_id: str) -> int:
    """Xóa tất cả chunks của 1 document khỏi collection"""
    client = _get_client()
    result = await client.delete(
        collection_name=collection_name,
        points_selector=Filter(
            must=[FieldCondition(key="document_id", match=MatchValue(value=document_id))]
        )
    )
    logger.info(f"Deleted chunks of document {document_id} from {collection_name}")
    return result.operation_id

