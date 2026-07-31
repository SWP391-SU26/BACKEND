package com.courseqa.repository;

import com.courseqa.model.entity.ChunkEmbedding;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkEmbeddingRepository extends JpaRepository<ChunkEmbedding, UUID> {
    interface CompressedEmbeddingView {
        UUID getChunkId();
        byte[] getEmbeddingCompressed();
    }

    Optional<ChunkEmbedding> findByChunkIdAndEmbeddingModelId(UUID chunkId, UUID embeddingModelId);

    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "8000"))
    List<ChunkEmbedding> findByEmbeddingModelIdAndChunkIdIn(UUID embeddingModelId, Collection<UUID> chunkIds);

    @Query(value = """
            SELECT chunk_id AS chunkId, embedding_compressed AS embeddingCompressed
            FROM chunk_embeddings
            WHERE embedding_model_id = :modelId
              AND chunk_id IN (:chunkIds)
              AND embedding_compressed IS NOT NULL
            """, nativeQuery = true)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "8000"))
    List<CompressedEmbeddingView> findCompressedByModelAndChunkIds(
            @Param("modelId") UUID modelId,
            @Param("chunkIds") Collection<UUID> chunkIds);
}
