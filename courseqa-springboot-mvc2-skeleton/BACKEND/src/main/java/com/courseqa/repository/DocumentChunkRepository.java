package com.courseqa.repository;

import com.courseqa.model.entity.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    interface CompressedChunkView {
        UUID getChunkId();
        UUID getDocumentId();
        Integer getChunkIndex();
        String getChunkStrategy();
        Integer getPageStart();
        Integer getPageEnd();
        byte[] getContentCompressed();
    }

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findByDocumentIdAndIsActiveTrueOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findByDocumentIdAndChunkVersionOrderByChunkIndexAsc(UUID documentId, Integer chunkVersion);

    @Query("SELECT COALESCE(MAX(c.chunkVersion), 0) FROM DocumentChunk c WHERE c.documentId = :documentId")
    Integer findMaxChunkVersion(@Param("documentId") UUID documentId);

    List<DocumentChunk> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    List<DocumentChunk> findByWorkspaceIdInOrderByCreatedAtAsc(List<UUID> workspaceIds);

    List<DocumentChunk> findByDocumentIdInOrderByCreatedAtAsc(List<UUID> documentIds);

    List<DocumentChunk> findByWorkspaceIdAndIsActiveTrueOrderByCreatedAtAsc(UUID workspaceId);

    List<DocumentChunk> findByWorkspaceIdInAndIsActiveTrueOrderByCreatedAtAsc(List<UUID> workspaceIds);

    List<DocumentChunk> findByDocumentIdInAndIsActiveTrueOrderByCreatedAtAsc(List<UUID> documentIds);

    @Query(value = """
            SELECT chunk_id AS chunkId, document_id AS documentId,
                   chunk_index AS chunkIndex, chunk_strategy AS chunkStrategy,
                   page_start AS pageStart, page_end AS pageEnd,
                   content_compressed AS contentCompressed
            FROM document_chunks
            WHERE document_id IN (:documentIds)
              AND content_compressed IS NOT NULL
              AND is_active = 1
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<CompressedChunkView> findCompressedByDocumentIds(
            @Param("documentIds") List<UUID> documentIds);

    @Query(value = """
            SELECT chunk_id AS chunkId, document_id AS documentId,
                   chunk_index AS chunkIndex, chunk_strategy AS chunkStrategy,
                   page_start AS pageStart, page_end AS pageEnd,
                   content_compressed AS contentCompressed
            FROM document_chunks
            WHERE workspace_id IN (:workspaceIds)
              AND content_compressed IS NOT NULL
              AND is_active = 1
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<CompressedChunkView> findCompressedByWorkspaceIds(
            @Param("workspaceIds") List<UUID> workspaceIds);

    void deleteByDocumentId(UUID documentId);

    /**
     * Moving a document to a new personal workspace must carry its chunks along:
     * retrieval scopes by workspaceId, so a document left with stale chunk
     * workspace ids would silently vanish from the new workspace's answers.
     */
    @Modifying
    @Query("UPDATE DocumentChunk c SET c.workspaceId = :workspaceId WHERE c.documentId = :documentId")
    int updateWorkspaceIdByDocumentId(@Param("documentId") UUID documentId, @Param("workspaceId") UUID workspaceId);
}
