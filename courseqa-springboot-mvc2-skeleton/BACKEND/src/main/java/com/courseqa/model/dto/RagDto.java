package com.courseqa.model.dto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.EmbeddingModel;
import com.courseqa.model.entity.RetrievalQuery;
import com.courseqa.model.entity.RetrievalResult;
import java.time.LocalDateTime;
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

    public static class EmbeddingModelResponse {
        public UUID embeddingModelId;
        public String modelName;
        public String provider;
        public Integer dimension;
        public Boolean isLocal;
        public String description;
        public String configJson;
        public Boolean isActive;
        public LocalDateTime createdAt;

        public static EmbeddingModelResponse fromEntity(EmbeddingModel model) {
            EmbeddingModelResponse response = new EmbeddingModelResponse();
            response.embeddingModelId = model.getEmbeddingModelId();
            response.modelName = model.getModelName();
            response.provider = model.getProvider();
            response.dimension = model.getDimension();
            response.isLocal = model.getIsLocal();
            response.description = model.getDescription();
            response.configJson = model.getConfigJson();
            response.isActive = model.getIsActive();
            response.createdAt = model.getCreatedAt();
            return response;
        }
    }

    public static class PrepareEmbeddingsRequest {
        public UUID workspaceId;
        public UUID documentId;
        public UUID embeddingModelId;
    }

    public static class PrepareEmbeddingsResponse {
        public UUID embeddingModelId;
        public int totalChunks;
        public int createdEmbeddings;
        public int skippedExisting;
    }

    public static class RetrievalRequest {
        public UUID chatSessionId;
        public UUID userMessageId;
        public UUID workspaceId;
        public List<UUID> workspaceIds;
        public List<UUID> documentIds;
        public UUID semesterId;
        public String scopeType;
        public String queryText;
        public UUID embeddingModelId;
        public Integer topK;
        public Double similarityThreshold;
    }

    public static class RetrievedChunk {
        public UUID retrievalResultId;
        public UUID chunkId;
        public UUID documentId;
        public String documentTitle;
        public String filename;
        public Integer pageStart;
        public Integer pageEnd;
        public Integer rank;
        public Double similarityScore;
        public String content;
    }

    public static class RetrievalQueryResponse {
        public UUID retrievalQueryId;
        public UUID workspaceId;
        public UUID semesterId;
        public String scopeType;
        public String queryText;
        public UUID embeddingModelId;
        public Integer topK;
        public Double similarityThreshold;
        public Boolean answerable;
        public String noAnswerReason;
        public Integer latencyMs;
        public LocalDateTime createdAt;

        public static RetrievalQueryResponse fromEntity(RetrievalQuery query) {
            RetrievalQueryResponse response = new RetrievalQueryResponse();
            response.retrievalQueryId = query.getRetrievalQueryId();
            response.workspaceId = query.getWorkspaceId();
            response.semesterId = query.getSemesterWorkspaceId();
            response.scopeType = query.getScopeType();
            response.queryText = query.getQueryText();
            response.embeddingModelId = query.getEmbeddingModelId();
            response.topK = query.getTopK();
            response.similarityThreshold = query.getSimilarityThreshold();
            response.answerable = query.getIsAnswerable();
            response.noAnswerReason = query.getNoAnswerReason();
            response.latencyMs = query.getLatencyMs();
            response.createdAt = query.getCreatedAt();
            return response;
        }
    }

    public static class RetrievalResultResponse {
        public UUID retrievalResultId;
        public UUID retrievalQueryId;
        public UUID chunkId;
        public UUID documentId;
        public Integer resultRank;
        public Double similarityScore;
        public Double rerankScore;
        public LocalDateTime createdAt;

        public static RetrievalResultResponse fromEntity(RetrievalResult result) {
            RetrievalResultResponse response = new RetrievalResultResponse();
            response.retrievalResultId = result.getRetrievalResultId();
            response.retrievalQueryId = result.getRetrievalQueryId();
            response.chunkId = result.getChunkId();
            response.documentId = result.getDocumentId();
            response.resultRank = result.getResultRank();
            response.similarityScore = result.getSimilarityScore();
            response.rerankScore = result.getRerankScore();
            response.createdAt = result.getCreatedAt();
            return response;
        }
    }

    public static class RetrievalResponse {
        public UUID retrievalQueryId;
        public UUID embeddingModelId;
        public String embeddingModelName;
        public Boolean answerable;
        public String noAnswerReason;
        public List<RetrievedChunk> results;
    }

    public static class CitationResponse {
        public UUID citationId;
        public UUID assistantMessageId;
        public UUID retrievalResultId;
        public UUID chunkId;
        public UUID documentId;
        public String documentTitle;
        public Integer pageStart;
        public Integer pageEnd;
        public String quoteText;

        public static CitationResponse fromEntity(AnswerCitation citation) {
            CitationResponse response = new CitationResponse();
            response.citationId = citation.getCitationId();
            response.assistantMessageId = citation.getAssistantMessageId();
            response.retrievalResultId = citation.getRetrievalResultId();
            response.chunkId = citation.getChunkId();
            response.documentId = citation.getDocumentId();
            response.documentTitle = citation.getDocumentTitle();
            response.pageStart = citation.getPageStart();
            response.pageEnd = citation.getPageEnd();
            response.quoteText = citation.getQuoteText();
            return response;
        }
    }
}
