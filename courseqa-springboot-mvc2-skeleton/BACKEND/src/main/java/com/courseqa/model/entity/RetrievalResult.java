package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "retrieval_results")
public class RetrievalResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "retrieval_result_id")
    private UUID retrievalResultId;

    @Column(name = "retrieval_query_id")
    private UUID retrievalQueryId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "result_rank")
    private Integer resultRank;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "rerank_score")
    private Double rerankScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public RetrievalResult() { }

    public UUID getRetrievalResultId() { return retrievalResultId; }
    public void setRetrievalResultId(UUID retrievalResultId) { this.retrievalResultId = retrievalResultId; }

    public UUID getRetrievalQueryId() { return retrievalQueryId; }
    public void setRetrievalQueryId(UUID retrievalQueryId) { this.retrievalQueryId = retrievalQueryId; }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public Integer getResultRank() { return resultRank; }
    public void setResultRank(Integer resultRank) { this.resultRank = resultRank; }

    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }

    public Double getRerankScore() { return rerankScore; }
    public void setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
