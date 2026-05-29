"""
Vector store module - Interface with Qdrant vector database
Handles document indexing and similarity search
"""

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct
from typing import List, Dict, Tuple, Optional
import uuid
import logging
from app.config import settings
from src.embeddings import get_embedding_provider

logger = logging.getLogger(__name__)


class QdrantStore:
    """
    Wrapper around Qdrant vector database
    """

    def __init__(self, host: str = None, port: int = None, model_name: str = None):
        """
        Initialize Qdrant connection
        
        Args:
            host: Qdrant server host
            port: Qdrant server port
            model_name: Embedding model to use for vectors
        """
        self.host = host or settings.qdrant_host
        self.port = port or settings.qdrant_port
        self.model_name = model_name or settings.embedding_model_default
        
        try:
            self.client = QdrantClient(host=self.host, port=self.port)
            logger.info(f"Connected to Qdrant at {self.host}:{self.port}")
        except Exception as e:
            logger.error(f"Failed to connect to Qdrant: {str(e)}")
            raise
        
        self.embeddings = get_embedding_provider(self.model_name)
        self.embedding_dim = self.embeddings.get_embedding_dimension()
        logger.info(f"Using embedding dimension: {self.embedding_dim}")

    def create_collection(self, collection_name: str, overwrite: bool = False) -> None:
        """
        Create a collection in Qdrant
        
        Args:
            collection_name: Name of collection
            overwrite: Whether to delete existing collection
        """
        try:
            if overwrite:
                self.client.delete_collection(collection_name)
                logger.info(f"Deleted existing collection: {collection_name}")
            
            self.client.create_collection(
                collection_name=collection_name,
                vectors_config=VectorParams(
                    size=self.embedding_dim,
                    distance=Distance.COSINE
                )
            )
            logger.info(f"Created collection: {collection_name}")
        except Exception as e:
            logger.error(f"Failed to create collection {collection_name}: {str(e)}")
            raise

    def upsert_documents(
        self,
        collection_name: str,
        documents: List[str],
        document_id: str = None,
        metadata: Dict = None
    ) -> List[str]:
        """
        Index documents into Qdrant collection
        
        Args:
            collection_name: Target collection name
            documents: List of text chunks
            document_id: Source document identifier
            metadata: Additional metadata
            
        Returns:
            List of Qdrant point IDs
        """
        if not documents:
            raise ValueError("No documents provided")
        
        # Embed all documents
        embeddings = self.embeddings.embed_texts(documents, normalize=True)
        
        # Create points
        points = []
        point_ids = []
        
        for idx, (doc, embedding) in enumerate(zip(documents, embeddings)):
            point_id = str(uuid.uuid4())
            point_ids.append(point_id)
            
            payload = {
                "text": doc,
                "document_id": document_id or f"doc_{idx}",
                "chunk_index": idx,
            }
            
            if metadata:
                payload.update(metadata)
            
            points.append(
                PointStruct(
                    id=point_id,
                    vector=embedding,
                    payload=payload
                )
            )
        
        # Upsert to Qdrant
        try:
            self.client.upsert(
                collection_name=collection_name,
                points=points
            )
            logger.info(
                f"Upserted {len(points)} points to {collection_name} "
                f"from document {document_id}"
            )
            return point_ids
        except Exception as e:
            logger.error(f"Failed to upsert documents: {str(e)}")
            raise

    def search(
        self,
        collection_name: str,
        query_text: str,
        top_k: int = 3,
        min_score: float = 0.0
    ) -> List[Dict]:
        """
        Search for similar documents
        
        Args:
            collection_name: Collection to search
            query_text: Query text
            top_k: Number of results
            min_score: Minimum similarity score
            
        Returns:
            List of search results with scores and text
        """
        # Embed query
        query_embedding = self.embeddings.embed_text(query_text, normalize=True)
        
        try:
            results = self.client.search(
                collection_name=collection_name,
                query_vector=query_embedding.tolist(),
                limit=top_k
            )
            
            # Format results
            formatted_results = []
            for hit in results:
                if hit.score >= min_score:
                    formatted_results.append({
                        "id": hit.id,
                        "score": hit.score,
                        "text": hit.payload.get("text", ""),
                        "document_id": hit.payload.get("document_id"),
                        "chunk_index": hit.payload.get("chunk_index"),
                        "metadata": {
                            k: v for k, v in hit.payload.items()
                            if k not in ["text", "document_id", "chunk_index"]
                        }
                    })
            
            logger.info(f"Found {len(formatted_results)} results for query")
            return formatted_results
        
        except Exception as e:
            logger.error(f"Search failed: {str(e)}")
            raise

    def get_collection_info(self, collection_name: str) -> Dict:
        """
        Get information about a collection
        
        Args:
            collection_name: Collection name
            
        Returns:
            Collection info
        """
        try:
            info = self.client.get_collection(collection_name)
            return {
                "name": collection_name,
                "points_count": info.points_count,
                "vector_size": info.config.params.vectors.size,
            }
        except Exception as e:
            logger.error(f"Failed to get collection info: {str(e)}")
            raise

    def delete_collection(self, collection_name: str) -> None:
        """Delete a collection"""
        try:
            self.client.delete_collection(collection_name)
            logger.info(f"Deleted collection: {collection_name}")
        except Exception as e:
            logger.error(f"Failed to delete collection: {str(e)}")
            raise


# Singleton instance
_vector_store = None


def get_vector_store(
    host: str = None,
    port: int = None,
    model_name: str = None
) -> QdrantStore:
    """
    Get or create vector store instance (singleton)
    
    Args:
        host: Qdrant host
        port: Qdrant port
        model_name: Embedding model name
        
    Returns:
        QdrantStore instance
    """
    global _vector_store
    
    if _vector_store is None:
        _vector_store = QdrantStore(host, port, model_name)
    
    return _vector_store
 
