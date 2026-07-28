package com.courseqa.repository;

import com.courseqa.model.entity.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    interface CompressedChunkView {
        UUID getChunkId();
        UUID getDocumentId();
        Integer getPageStart();
        Integer getPageEnd();
        byte[] getContentCompressed();
    }

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    List<DocumentChunk> findByWorkspaceIdInOrderByCreatedAtAsc(List<UUID> workspaceIds);

    List<DocumentChunk> findByDocumentIdInOrderByCreatedAtAsc(List<UUID> documentIds);

    @Query(value = """
            SELECT chunk_id AS chunkId, document_id AS documentId,
                   page_start AS pageStart, page_end AS pageEnd,
                   content_compressed AS contentCompressed
            FROM document_chunks
            WHERE document_id IN (:documentIds)
              AND content_compressed IS NOT NULL
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<CompressedChunkView> findCompressedByDocumentIds(
            @Param("documentIds") List<UUID> documentIds);

    @Query(value = """
            SELECT chunk_id AS chunkId, document_id AS documentId,
                   page_start AS pageStart, page_end AS pageEnd,
                   content_compressed AS contentCompressed
            FROM document_chunks
            WHERE workspace_id IN (:workspaceIds)
              AND content_compressed IS NOT NULL
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<CompressedChunkView> findCompressedByWorkspaceIds(
            @Param("workspaceIds") List<UUID> workspaceIds);

    void deleteByDocumentId(UUID documentId);
}
