package com.courseqa.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// TODO: Add JPA annotations: @Entity, @Table
// TODO: Add fields matching database table
// TODO: Add constructors, getters, setters
@Entity
@Table(name = "chunk_embeddings")
public class ChunkEmbedding {
 @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chunk_embedding_id")
    private UUID chunkEmbeddingId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "embedding_json", columnDefinition = "NVARCHAR(MAX)")
    private String embeddingJson;

    @Column(name = "dimension")
    private Integer dimension;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ChunkEmbedding() { }

    public UUID getChunkEmbeddingId() { return chunkEmbeddingId; }
    public void setChunkEmbeddingId(UUID chunkEmbeddingId) { this.chunkEmbeddingId = chunkEmbeddingId; }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public UUID getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(UUID embeddingModelId) { this.embeddingModelId = embeddingModelId; }

    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }

    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
