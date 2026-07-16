package com.courseqa.repository;

import com.courseqa.model.entity.SavedNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedNoteRepository extends JpaRepository<SavedNote, UUID> {
    List<SavedNote> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    List<SavedNote> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID userId);
}
