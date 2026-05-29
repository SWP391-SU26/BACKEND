"""
Evaluator module - Compare RAG and fine-tuned answers
Select winner or mark as tie
"""

from typing import Dict, List
import logging
from src.embeddings import get_embedding_provider

logger = logging.getLogger(__name__)


class AnswerEvaluator:
    """
    Evaluate and compare two answers using semantic similarity and heuristics
    """

    def __init__(self, model_name: str = None):
        """
        Initialize evaluator
        
        Args:
            model_name: Embedding model for similarity calculation
        """
        self.embeddings = get_embedding_provider(model_name)

    def semantic_similarity(self, text1: str, text2: str) -> float:
        """
        Calculate semantic similarity between two texts
        
        Args:
            text1: First text
            text2: Second text
            
        Returns:
            Similarity score (0-1)
        """
        return self.embeddings.similarity_score(text1, text2)

    def evaluate_answer_quality(
        self,
        answer: str,
        question: str,
        confidence_score: float
    ) -> float:
        """
        Evaluate single answer quality (0-100)
        
        Based on:
        - Semantic relevance to question
        - Confidence score from model
        - Text length and structure
        
        Args:
            answer: Answer text
            question: Original question
            confidence_score: Model confidence (0-100)
            
        Returns:
            Quality score (0-100)
        """
        if not answer or len(answer) < 20:
            return 20.0
        
        # Semantic relevance (0-50 points)
        relevance = self.semantic_similarity(question, answer)
        relevance_score = relevance * 50
        
        # Confidence contribution (0-50 points)
        confidence_contribution = (confidence_score / 100) * 50
        
        total_score = relevance_score + confidence_contribution
        logger.info(f"Answer quality: {total_score:.1f} (relevance: {relevance_score:.1f}, confidence: {confidence_contribution:.1f})")
        
        return min(total_score, 100.0)

    def compare_answers(
        self,
        question: str,
        rag_answer: str,
        rag_score: float,
        finetuned_answer: str,
        finetuned_score: float,
        threshold: float = 15.0
    ) -> Dict:
        """
        Compare two answers and determine winner
        
        Args:
            question: Original question
            rag_answer: Answer from RAG pipeline
            rag_score: RAG confidence score
            finetuned_answer: Answer from fine-tuned model
            finetuned_score: Fine-tuned model confidence
            threshold: Minimum score difference to declare winner
            
        Returns:
            Dict with winner, scores, and recommendation
        """
        # Evaluate both answers
        rag_quality = self.evaluate_answer_quality(rag_answer, question, rag_score)
        finetuned_quality = self.evaluate_answer_quality(
            finetuned_answer, question, finetuned_score
        )
        
        # Calculate difference
        score_diff = abs(rag_quality - finetuned_quality)
        
        # Determine winner
        if score_diff < threshold:
            # Tie - both answers are similar quality
            logger.info(f"Tie: Both answers similar (RAG: {rag_quality:.1f}, FT: {finetuned_quality:.1f})")
            winner = "tie"
            recommendation = (
                "Cả hai câu trả lời có chất lượng tương đương. "
                "Vui lòng chọn câu trả lời bạn thích hợp nhất."
            )
            show_both = True
        elif rag_quality > finetuned_quality:
            logger.info(f"RAG wins: {rag_quality:.1f} > {finetuned_quality:.1f}")
            winner = "rag"
            diff_pct = ((rag_quality - finetuned_quality) / finetuned_quality * 100) if finetuned_quality > 0 else 0
            recommendation = (
                f"Câu trả lời từ RAG có chất lượng cao hơn "
                f"({diff_pct:.0f}% tốt hơn fine-tuned model)."
            )
            show_both = False
        else:
            logger.info(f"Fine-tuned wins: {finetuned_quality:.1f} > {rag_quality:.1f}")
            winner = "finetuned"
            diff_pct = ((finetuned_quality - rag_quality) / rag_quality * 100) if rag_quality > 0 else 0
            recommendation = (
                f"Câu trả lời từ fine-tuned model có chất lượng cao hơn "
                f"({diff_pct:.0f}% tốt hơn RAG)."
            )
            show_both = False
        
        return {
            "winner": winner,
            "show_both": show_both,
            "scores": {
                "rag": rag_quality,
                "finetuned": finetuned_quality,
                "difference": score_diff
            },
            "recommendation": recommendation,
            "semantic_similarity": self.semantic_similarity(rag_answer, finetuned_answer)
        }

    def calculate_answer_length_score(self, answer: str) -> float:
        """
        Score answer based on length (too short or too long is bad)
        
        Args:
            answer: Answer text
            
        Returns:
            Length score (0-100)
        """
        word_count = len(answer.split())
        
        # Ideal: 50-200 words
        if 50 <= word_count <= 200:
            return 100.0
        elif 30 <= word_count < 50 or 200 < word_count <= 300:
            return 80.0
        elif word_count < 30 or word_count > 300:
            return 50.0
        else:
            return 0.0

    def is_hallucination_likely(self, answer: str, context: List[str]) -> bool:
        """
        Detect if answer might be hallucination (not supported by context)
        
        Args:
            answer: Generated answer
            context: Retrieved context chunks
            
        Returns:
            True if hallucination is likely
        """
        if not context:
            return True
        
        # Calculate similarity of answer to context
        avg_similarity = sum(
            self.semantic_similarity(answer, chunk) for chunk in context
        ) / len(context)
        
        # If very low similarity, likely hallucination
        is_likely = avg_similarity < 0.3
        
        if is_likely:
            logger.warning(f"Possible hallucination detected (similarity: {avg_similarity:.3f})")
        
        return is_likely


def create_evaluator(model_name: str = None) -> AnswerEvaluator:
    """
    Factory function to create evaluator
    
    Args:
        model_name: Embedding model name
        
    Returns:
        AnswerEvaluator instance
    """
    return AnswerEvaluator(model_name)
 
