"""
Embeddings module - Wrapper for multiple embedding models
Supports: bge-m3, sentence-transformers, and custom models
"""

import numpy as np
from sentence_transformers import SentenceTransformer
from typing import List, Union
import logging
from app.config import settings

logger = logging.getLogger(__name__)

# Cache for loaded models to avoid reloading
MODEL_CACHE = {}


class EmbeddingProvider:
    """
    Unified interface for different embedding models
    """

    def __init__(self, model_name: str = None):
        """
        Initialize embedding provider
        
        Args:
            model_name: Name of embedding model (e.g., 'BAAI/bge-m3')
        """
        self.model_name = model_name or settings.embedding_model_default
        self.model = self._load_model(self.model_name)

    def _load_model(self, model_name: str) -> SentenceTransformer:
        """
        Load embedding model with caching
        
        Args:
            model_name: HuggingFace model identifier
            
        Returns:
            SentenceTransformer model instance
        """
        if model_name not in MODEL_CACHE:
            logger.info(f"Loading embedding model: {model_name}")
            try:
                MODEL_CACHE[model_name] = SentenceTransformer(model_name)
                logger.info(f"Successfully loaded: {model_name}")
            except Exception as e:
                logger.error(f"Failed to load embedding model {model_name}: {str(e)}")
                raise
        return MODEL_CACHE[model_name]

    def embed_text(self, text: str, normalize: bool = True) -> np.ndarray:
        """
        Embed a single text string
        
        Args:
            text: Text to embed
            normalize: Whether to normalize embeddings
            
        Returns:
            Embedding vector (1D numpy array)
        """
        if not text or not isinstance(text, str):
            raise ValueError("Input must be non-empty string")
        
        embedding = self.model.encode(text, normalize_embeddings=normalize)
        return embedding

    def embed_texts(self, texts: List[str], normalize: bool = True) -> List[np.ndarray]:
        """
        Embed multiple texts
        
        Args:
            texts: List of text strings
            normalize: Whether to normalize embeddings
            
        Returns:
            List of embedding vectors
        """
        if not texts or not all(isinstance(t, str) for t in texts):
            raise ValueError("Input must be list of non-empty strings")
        
        embeddings = self.model.encode(texts, normalize_embeddings=normalize)
        return embeddings.tolist()

    def get_embedding_dimension(self) -> int:
        """
        Get dimension of embeddings from this model
        
        Returns:
            Dimension (e.g., 768 for bge-m3)
        """
        # Encode a dummy text to get dimension
        dummy = self.embed_text("dummy")
        return len(dummy)

    def similarity_score(self, text1: str, text2: str) -> float:
        """
        Calculate cosine similarity between two texts
        
        Args:
            text1: First text
            text2: Second text
            
        Returns:
            Similarity score (0-1)
        """
        emb1 = self.embed_text(text1, normalize=True)
        emb2 = self.embed_text(text2, normalize=True)
        
        # Cosine similarity (dot product with normalized vectors)
        similarity = np.dot(emb1, emb2)
        return float(similarity)


# Singleton instances for each model
_providers = {}


def get_embedding_provider(model_name: str = None) -> EmbeddingProvider:
    """
    Get or create embedding provider (singleton per model)
    
    Args:
        model_name: Embedding model name
        
    Returns:
        EmbeddingProvider instance
    """
    if model_name is None:
        model_name = settings.embedding_model_default
    
    if model_name not in _providers:
        _providers[model_name] = EmbeddingProvider(model_name)
    
    return _providers[model_name]


def embed_question(question: str, model_name: str = None) -> np.ndarray:
    """
    Quick function to embed a question
    
    Args:
        question: Question text
        model_name: Embedding model to use
        
    Returns:
        Question embedding
    """
    provider = get_embedding_provider(model_name)
    return provider.embed_text(question, normalize=True)


def embed_documents(documents: List[str], model_name: str = None) -> List[np.ndarray]:
    """
    Quick function to embed multiple documents
    
    Args:
        documents: List of document texts
        model_name: Embedding model to use
        
    Returns:
        List of document embeddings
    """
    provider = get_embedding_provider(model_name)
    return provider.embed_texts(documents, normalize=True)
 
