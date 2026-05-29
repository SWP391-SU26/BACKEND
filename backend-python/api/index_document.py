"""POST /ai/index-document — Java gọi sau khi upload file xong"""

from fastapi import APIRouter, HTTPException
from loguru import logger
from models.requests import IndexDocumentRequest
from models.responses import IndexDocumentResponse
from src.embeddings import embed_texts
from src.vector_store import upsert_chunks

router = APIRouter()


@router.post("/index-document", response_model=IndexDocumentResponse)
async def index_document(request: IndexDocumentRequest):
    """Nhận chunks từ Java, embed và lưu vào Qdrant"""
    logger.info(f"Indexing document {request.document_id}: {len(request.chunks)} chunks, model={request.embedding_model}")

    try:
        texts = [chunk.content for chunk in request.chunks]
        vectors = await embed_texts(texts, request.embedding_model.value)

        point_ids = await upsert_chunks(
            collection_name=request.collection_name,
            chunks=[c.model_dump() for c in request.chunks],
            vectors=vectors,
            document_id=request.document_id
        )

        return IndexDocumentResponse(
            collection_name=request.collection_name,
            point_ids=point_ids,
            total_chunks=len(request.chunks),
            status="success"
        )

    except Exception as e:
        logger.error(f"Index failed for {request.document_id}: {e}")
        raise HTTPException(status_code=500, detail={"code": "IDX-002", "message": str(e)})

