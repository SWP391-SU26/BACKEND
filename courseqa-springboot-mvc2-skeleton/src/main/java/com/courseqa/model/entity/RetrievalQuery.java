package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "retrieval_queries")
public class RetrievalQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "retrieval_query_id")
    private UUID retrievalQueryId;

    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @Column(name = "user_message_id")
    private UUID userMessageId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "query_text", columnDefinition = "NVARCHAR(MAX)")
    private String queryText;

    @Column(name = "rewritten_query", columnDefinition = "NVARCHAR(MAX)")
    private String rewrittenQuery;

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "similarity_metric")
    private String similarityMetric;

    @Column(name = "similarity_threshold")
    private Double similarityThreshold;

    @Column(name = "is_answerable")
    private Boolean isAnswerable;

    @Column(name = "no_answer_reason", columnDefinition = "NVARCHAR(MAX)")
    private String noAnswerReason;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public RetrievalQuery() { }

    public UUID getRetrievalQueryId() { return retrievalQueryId; }
    public void setRetrievalQueryId(UUID retrievalQueryId) { this.retrievalQueryId = retrievalQueryId; }

    public UUID getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(UUID chatSessionId) { this.chatSessionId = chatSessionId; }

    public UUID getUserMessageId() { return userMessageId; }
    public void setUserMessageId(UUID userMessageId) { this.userMessageId = userMessageId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }

    public UUID getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(UUID embeddingModelId) { this.embeddingModelId = embeddingModelId; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public String getSimilarityMetric() { return similarityMetric; }
    public void setSimilarityMetric(String similarityMetric) { this.similarityMetric = similarityMetric; }

    public Double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(Double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

    public Boolean getIsAnswerable() { return isAnswerable; }
    public void setIsAnswerable(Boolean isAnswerable) { this.isAnswerable = isAnswerable; }

    public String getNoAnswerReason() { return noAnswerReason; }
    public void setNoAnswerReason(String noAnswerReason) { this.noAnswerReason = noAnswerReason; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
