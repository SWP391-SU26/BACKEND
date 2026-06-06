package com.courseqa.model.dto;
import java.util.List;
import java.util.UUID;

// DTOs for retrieval, embedding, citation responses.
// TODO: Add request/response DTO classes here.

public class RagDto {
public static class CreateEmbeddingModelRequest {
        public String modelName;
        public String provider;
        public Integer dimension;
        public Boolean isLocal;
        public String description;
        public String configJson;
        public Boolean isActive;
    }

    public static class RetrievalRequest {
        public UUID chatSessionId;
        public UUID userMessageId;
        public UUID workspaceId;
        public String queryText;
        public UUID embeddingModelId;
        public Integer topK;
        public Double similarityThreshold;
    }

    public static class RetrievedChunk {
        public UUID chunkId;
        public UUID documentId;
        public Integer rank;
        public Double similarityScore;
        public String content;
    }

    public static class RetrievalResponse {
        public UUID retrievalQueryId;
        public Boolean answerable;
        public String noAnswerReason;
        public List<RetrievedChunk> results;
    }

    public static class CitationResponse {
        public UUID citationId;
        public UUID assistantMessageId;
        public String documentTitle;
        public Integer pageStart;
        public Integer pageEnd;
        public String quoteText;
    }
}
