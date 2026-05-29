"""
Text chunking module - Split documents into chunks for embeddings
Supports multiple chunking strategies
"""

from langchain_text_splitters import RecursiveCharacterTextSplitter, CharacterTextSplitter
from typing import List, Optional
import logging

logger = logging.getLogger(__name__)


class TextChunker:
    """
    Text chunking with multiple strategies
    """

    def __init__(
        self,
        chunk_size: int = 500,
        chunk_overlap: int = 100,
        strategy: str = "recursive"
    ):
        """
        Initialize text chunker
        
        Args:
            chunk_size: Size of each chunk in characters
            chunk_overlap: Overlap between consecutive chunks
            strategy: 'recursive', 'character', or 'token'
        """
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        self.strategy = strategy
        
        if strategy == "recursive":
            self.splitter = RecursiveCharacterTextSplitter(
                chunk_size=chunk_size,
                chunk_overlap=chunk_overlap,
                separators=["\n\n", "\n", " ", ""],
                length_function=len
            )
        elif strategy == "character":
            self.splitter = CharacterTextSplitter(
                chunk_size=chunk_size,
                chunk_overlap=chunk_overlap,
                separator="\n\n"
            )
        else:
            raise ValueError(f"Unknown chunking strategy: {strategy}")
        
        logger.info(
            f"Initialized {strategy} chunker: "
            f"size={chunk_size}, overlap={chunk_overlap}"
        )

    def chunk_text(self, text: str) -> List[str]:
        """
        Split text into chunks
        
        Args:
            text: Raw text to chunk
            
        Returns:
            List of text chunks
        """
        if not text or not isinstance(text, str):
            logger.warning("Empty or invalid text provided to chunker")
            return []
        
        chunks = self.splitter.split_text(text)
        logger.info(f"Split text into {len(chunks)} chunks")
        return chunks

    def chunk_texts(self, texts: List[str]) -> List[str]:
        """
        Split multiple texts into chunks
        
        Args:
            texts: List of texts to chunk
            
        Returns:
            Combined list of all chunks
        """
        all_chunks = []
        for text in texts:
            chunks = self.chunk_text(text)
            all_chunks.extend(chunks)
        
        logger.info(f"Total chunks from {len(texts)} texts: {len(all_chunks)}")
        return all_chunks


class ChunkingStrategies:
    """
    Pre-configured chunking strategies for different use cases
    """

    @staticmethod
    def dense_chunks() -> TextChunker:
        """Dense chunks for detailed semantic search"""
        return TextChunker(chunk_size=300, chunk_overlap=50, strategy="recursive")

    @staticmethod
    def medium_chunks() -> TextChunker:
        """Medium chunks - default"""
        return TextChunker(chunk_size=500, chunk_overlap=100, strategy="recursive")

    @staticmethod
    def sparse_chunks() -> TextChunker:
        """Sparse chunks for broad context"""
        return TextChunker(chunk_size=800, chunk_overlap=150, strategy="recursive")

    @staticmethod
    def get_chunker(chunk_size: int = 500, chunk_overlap: int = 100) -> TextChunker:
        """Factory method to get custom chunker"""
        return TextChunker(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            strategy="recursive"
        )


# Default chunker instance
_default_chunker = None


def get_default_chunker(
    chunk_size: int = 500,
    chunk_overlap: int = 100,
    strategy: str = "recursive"
) -> TextChunker:
    """
    Get or create default chunker instance (singleton)
    
    Args:
        chunk_size: Size of each chunk
        chunk_overlap: Overlap between chunks
        strategy: Chunking strategy
        
    Returns:
        TextChunker instance
    """
    global _default_chunker
    
    if _default_chunker is None:
        _default_chunker = TextChunker(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            strategy=strategy
        )
    
    return _default_chunker


def chunk_text(text: str, chunk_size: int = 500, chunk_overlap: int = 100) -> List[str]:
    """
    Quick function to chunk text with default settings
    
    Args:
        text: Text to chunk
        chunk_size: Size of chunks
        chunk_overlap: Overlap between chunks
        
    Returns:
        List of chunks
    """
    chunker = ChunkingStrategies.get_chunker(chunk_size, chunk_overlap)
    return chunker.chunk_text(text)
 
