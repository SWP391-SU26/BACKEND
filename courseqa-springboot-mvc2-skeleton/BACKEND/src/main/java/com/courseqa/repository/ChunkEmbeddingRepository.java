package com.courseqa.repository;

import com.courseqa.model.entity.ChunkEmbedding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkEmbeddingRepository extends JpaRepository<ChunkEmbedding, UUID> {
    Optional<ChunkEmbedding> findByChunkIdAndEmbeddingModelId(UUID chunkId, UUID embeddingModelId);
}
