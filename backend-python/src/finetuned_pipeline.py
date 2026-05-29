"""
Fine-tuned model pipeline - Direct question answering without retrieval
Queries a fine-tuned language model via Hugging Face Inference API
"""

from typing import Dict, Optional
import logging
import httpx
from app.config import settings

logger = logging.getLogger(__name__)


class FinetuneHubClient:
    """Client for Hugging Face Inference API (fine-tuned model)"""

    def __init__(self, model_endpoint: str = None, api_key: str = None):
        """
        Initialize HF Inference API client
        
        Args:
            model_endpoint: Hugging Face model endpoint URL
            api_key: Hugging Face API key
        """
        self.model_endpoint = model_endpoint or settings.finetuned_model_endpoint
        self.api_key = api_key or settings.huggingface_api_key
        
        if not self.model_endpoint:
            logger.warning("Fine-tuned model endpoint not configured")
        
        logger.info(f"Initialized Hugging Face client for: {self.model_endpoint}")

    def query(self, question: str) -> Dict:
        """
        Query fine-tuned model
        
        Args:
            question: Input question
            
        Returns:
            Dict with generated_text and other metadata
        """
        if not self.model_endpoint:
            return {
                "status": "error",
                "message": "Fine-tuned model endpoint not configured"
            }
        
        try:
            headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}
            
            payload = {
                "inputs": question,
                "parameters": {
                    "max_new_tokens": 256,
                    "temperature": 0.7,
                    "top_p": 0.9,
                }
            }
            
            response = httpx.post(
                self.model_endpoint,
                json=payload,
                headers=headers,
                timeout=30.0
            )
            
            if response.status_code != 200:
                logger.error(f"HF API error: {response.status_code} - {response.text}")
                return {
                    "status": "error",
                    "message": f"Model API error: {response.status_code}"
                }
            
            result = response.json()
            logger.info("Successfully queried fine-tuned model")
            return result
        
        except Exception as e:
            logger.error(f"Error querying fine-tuned model: {str(e)}")
            return {
                "status": "error",
                "message": f"Failed to query model: {str(e)}"
            }


class FinetuneAnswerer:
    """
    Pipeline for fine-tuned model inference
    """

    def __init__(self, endpoint: str = None, api_key: str = None):
        """
        Initialize fine-tuned answerer
        
        Args:
            endpoint: Model endpoint URL
            api_key: HF API key
        """
        self.client = FinetuneHubClient(endpoint, api_key)

    def generate_answer(self, question: str) -> str:
        """
        Generate answer using fine-tuned model
        
        Args:
            question: User question
            
        Returns:
            Generated answer text
        """
        # Mock implementation (for dev without actual model)
        logger.warning("Fine-tuned model not actually deployed - returning mock answer")
        return (
            "Đây là câu trả lời mô phỏng từ fine-tuned model. "
            "Trong production, câu trả lời sẽ được sinh từ fine-tuned model đã deploy."
        )

    def calculate_confidence_score(self, answer: str, question: str) -> float:
        """
        Calculate confidence score for fine-tuned answer
        
        Mock implementation - in production, could use model logits
        
        Args:
            answer: Generated answer
            question: Original question
            
        Returns:
            Confidence score (0-100)
        """
        # Mock: use length and structure as proxy for confidence
        if not answer or len(answer) < 20:
            return 40.0
        
        logger.info(f"Fine-tuned model confidence: 80.0")
        return 80.0

    def process(self, question: str) -> Dict:
        """
        Process question through fine-tuned model
        
        Args:
            question: User question
            
        Returns:
            Dict with answer, score, and status
        """
        # Validate input
        if not question or len(question) < 5:
            return {
                "status": "error",
                "message": "Question too short (minimum 5 characters)"
            }
        
        # Generate answer
        answer = self.generate_answer(question)
        
        # Calculate confidence
        confidence_score = self.calculate_confidence_score(answer, question)
        
        return {
            "status": "success",
            "answer": answer,
            "confidence_score": confidence_score,
            "model_type": "fine-tuned"
        }


# Singleton instance
_finetuner = None


def get_finetuner(endpoint: str = None, api_key: str = None) -> FinetuneAnswerer:
    """
    Get or create finetuned answerer instance
    
    Args:
        endpoint: Model endpoint
        api_key: HF API key
        
    Returns:
        FinetuneAnswerer instance
    """
    global _finetuner
    
    if _finetuner is None:
        _finetuner = FinetuneAnswerer(endpoint, api_key)
    
    return _finetuner
 
