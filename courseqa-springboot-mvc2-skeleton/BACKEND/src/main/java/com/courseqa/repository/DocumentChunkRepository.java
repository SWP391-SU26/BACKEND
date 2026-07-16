package com.courseqa.repository;

import com.courseqa.model.entity.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    List<DocumentChunk> findByWorkspaceIdInOrderByCreatedAtAsc(List<UUID> workspaceIds);

    List<DocumentChunk> findByDocumentIdInOrderByCreatedAtAsc(List<UUID> documentIds);

    void deleteByDocumentId(UUID documentId);
}
