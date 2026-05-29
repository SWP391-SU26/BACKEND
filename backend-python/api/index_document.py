"""
Document indexing endpoint
Endpoint: POST /ai/index-document
Called by Java to index documents into vector database
"""

from typing import Optional
from fastapi import APIRouter, HTTPException
from models.requests import IndexDocumentRequest
from models.responses import IndexDocumentResponse, StatusEnum
from src.vector_store import get_vector_store
from src.chunker import get_default_chunker
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["Document"])


@router.post("/index-document", response_model=IndexDocumentResponse)
async def index_document(request: IndexDocumentRequest) -> IndexDocumentResponse:
    """
    Index a document into the vector database
    
    Called by Java BE after uploading and chunking a document.
    Embeds chunks and stores in Qdrant collection.
    
    Args:
        request: IndexDocumentRequest with document_id, chunks, embedding_model
        
    Returns:
        IndexDocumentResponse with collection_name and point_ids
    """
    try:
        logger.info(
            f"Indexing document: {request.document_id} "
            f"({len(request.chunks)} chunks)"
        )
        
        # Validate input
        if not request.document_id or not request.chunks:
            raise ValueError("document_id and chunks are required")
        
        if len(request.chunks) == 0:
            raise ValueError("No chunks provided")
        
        # Get vector store
        vector_store = get_vector_store(model_name=request.embedding_model)
        
        # Create collection name based on model
        # Format: e1_e5base_300_k3 (embedding1_model_chunksize_topk)
        collection_name = f"collection_{request.embedding_model.replace('/', '_').lower()}"
        
        # Create collection if not exists
        try:
            vector_store.get_collection_info(collection_name)
        except Exception:
            logger.info(f"Creating new collection: {collection_name}")
            vector_store.create_collection(collection_name)
        
        # Upsert documents
        point_ids = vector_store.upsert_documents(
            collection_name=collection_name,
            documents=request.chunks,
            document_id=request.document_id,
            metadata=request.metadata or {}
        )
        
        logger.info(
            f"Successfully indexed {len(point_ids)} chunks "
            f"to collection {collection_name}"
        )
        
        return IndexDocumentResponse(
            status=StatusEnum.SUCCESS,
            collection_name=collection_name,
            point_ids=point_ids,
            message=f"Document indexed: {len(point_ids)} chunks"
        )
    
    except ValueError as e:
        logger.error(f"Validation error: {str(e)}")
        raise HTTPException(status_code=400, detail=str(e))
    
    except Exception as e:
        logger.error(f"Error indexing document: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Indexing failed: {str(e)}")
 
