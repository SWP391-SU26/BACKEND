package com.courseqa.repository;

import com.courseqa.model.entity.CourseDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, UUID> {
    List<CourseDocument> findAllByOrderByUploadedAtDesc();

    List<CourseDocument> findByUploadedByOrderByUploadedAtDesc(UUID uploadedBy);

    List<CourseDocument> findByWorkspaceIdOrderByUploadedAtDesc(UUID workspaceId);

    List<CourseDocument> findByWorkspaceIdAndUploadedByOrderByUploadedAtDesc(UUID workspaceId, UUID uploadedBy);
}
