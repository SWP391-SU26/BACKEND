package com.courseqa.repository;

import com.courseqa.model.entity.CourseDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, UUID> {
    List<CourseDocument> findByWorkspaceIdOrderByUploadedAtDesc(UUID workspaceId);
}
