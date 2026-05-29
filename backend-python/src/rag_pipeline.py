"""
RAG (Retrieval Augmented Generation) pipeline
Combines document retrieval with LLM for context-aware answers
"""

from typing import List, Dict, Optional, Tuple
import logging
from app.config import settings
from src.vector_store import get_vector_store
from src.embeddings import embed_question

logger = logging.getLogger(__name__)


class RAGPipeline:
    """
    RAG pipeline: retrieve relevant documents, build context, query LLM
    """

    def __init__(
        self,
        collection_name: str,
        embedding_model: str = None,
        top_k: int = 3,
        min_relevance_score: float = 0.5
    ):
        """
        Initialize RAG pipeline
        
        Args:
            collection_name: Qdrant collection name
            embedding_model: Embedding model to use
            top_k: Number of top results to retrieve
            min_relevance_score: Minimum relevance threshold
        """
        self.collection_name = collection_name
        self.embedding_model = embedding_model or settings.embedding_model_default
        self.top_k = top_k
        self.min_relevance_score = min_relevance_score
        
        self.vector_store = get_vector_store(model_name=self.embedding_model)
        logger.info(f"Initialized RAG pipeline for collection: {collection_name}")

    def retrieve_context(self, question: str) -> Tuple[List[str], List[Dict]]:
        """
        Retrieve relevant chunks from vector database
        
        Args:
            question: User question
            
        Returns:
            Tuple of (retrieved_texts, metadata)
        """
        try:
            results = self.vector_store.search(
                collection_name=self.collection_name,
                query_text=question,
                top_k=self.top_k,
                min_score=self.min_relevance_score
            )
            
            if not results:
                logger.warning(f"No relevant documents found for question")
                return [], []
            
            texts = [r["text"] for r in results]
            metadata = [
                {
                    "id": r["id"],
                    "score": r["score"],
                    "document_id": r["document_id"],
                    "chunk_index": r["chunk_index"]
                }
                for r in results
            ]
            
            logger.info(f"Retrieved {len(texts)} relevant chunks")
            return texts, metadata
        
        except Exception as e:
            logger.error(f"Error retrieving context: {str(e)}")
            return [], []

    def build_prompt(
        self,
        question: str,
        context_chunks: List[str]
    ) -> str:
        """
        Build LLM prompt with question and context
        
        Args:
            question: User question
            context_chunks: Retrieved context chunks
            
        Returns:
            Formatted prompt for LLM
        """
        context_text = "\n\n".join(context_chunks)
        
        prompt = f"""Bạn là một trợ lý giáo dục hữu ích. Trả lời câu hỏi dưới đây dựa trên tài liệu được cung cấp.

Tài liệu tham khảo:
{context_text}

Câu hỏi: {question}

Hướng dẫn:
1. Chỉ sử dụng thông tin từ tài liệu cung cấp
2. Nếu không tìm thấy câu trả lời, hãy nói rõ "Không tìm thấy trong tài liệu"
3. Trả lời rõ ràng, ngắn gọn (không quá 500 từ)
4. Sử dụng tiếng Việt

Trả lời:"""
        
        return prompt

    def generate_answer_with_llm(self, prompt: str) -> str:
        """
        Query LLM to generate answer (mock implementation)
        
        In production, this calls OpenAI/Gemini API
        
        Args:
            prompt: Formatted prompt for LLM
            
        Returns:
            LLM-generated answer
        """
        # TODO: Integrate with OpenAI or Gemini API
        # For now, return mock response
        logger.warning("LLM integration not implemented - returning mock answer")
        return (
            "Đây là câu trả lời mô phỏng từ RAG pipeline. "
            "Trong production, câu trả lời sẽ được sinh từ LLM API."
        )

    def calculate_confidence_score(
        self,
        question: str,
        retrieved_scores: List[float]
    ) -> float:
        """
        Calculate RAG confidence score based on retrieval quality
        
        Args:
            question: User question
            retrieved_scores: Relevance scores from vector search
            
        Returns:
            Confidence score (0-100)
        """
        if not retrieved_scores:
            return 0.0
        
        # Average of top scores, scaled to 0-100
        avg_score = sum(retrieved_scores) / len(retrieved_scores)
        confidence = avg_score * 100
        
        logger.info(f"RAG confidence score: {confidence:.1f}")
        return confidence

    def process(self, question: str) -> Dict:
        """
        Full RAG pipeline: retrieve → build prompt → query LLM → return answer
        
        Args:
            question: User question
            
        Returns:
            Dict with answer, citations, and confidence score
        """
        # Validate input
        if not question or len(question) < 5:
            return {
                "status": "error",
                "message": "Question too short (minimum 5 characters)"
            }
        
        # Step 1: Retrieve context
        context_chunks, metadata = self.retrieve_context(question)
        
        if not context_chunks:
            return {
                "status": "error",
                "message": "No relevant documents found in knowledge base"
            }
        
        # Step 2: Build prompt
        prompt = self.build_prompt(question, context_chunks)
        
        # Step 3: Generate answer
        answer = self.generate_answer_with_llm(prompt)
        
        # Step 4: Calculate confidence
        relevance_scores = [m["score"] for m in metadata]
        confidence_score = self.calculate_confidence_score(question, relevance_scores)
        
        # Step 5: Format citations
        citations = [
            {
                "document_id": m["document_id"],
                "chunk_index": m["chunk_index"],
                "relevance_score": m["score"],
                "text_preview": context_chunks[i][:200] + "..."
            }
            for i, m in enumerate(metadata)
        ]
        
        return {
            "status": "success",
            "answer": answer,
            "citations": citations,
            "confidence_score": confidence_score,
            "num_chunks_retrieved": len(context_chunks)
        }


def create_rag_pipeline(
    collection_name: str,
    embedding_model: str = None,
    top_k: int = 3
) -> RAGPipeline:
    """
    Factory function to create RAG pipeline
    
    Args:
        collection_name: Qdrant collection name
        embedding_model: Embedding model
        top_k: Number of results to retrieve
        
    Returns:
        Initialized RAGPipeline instance
    """
    return RAGPipeline(
        collection_name=collection_name,
        embedding_model=embedding_model,
        top_k=top_k
    )
 
