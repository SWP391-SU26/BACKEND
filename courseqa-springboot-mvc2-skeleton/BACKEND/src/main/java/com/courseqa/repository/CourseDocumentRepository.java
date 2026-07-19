package com.courseqa.repository;

import com.courseqa.model.entity.CourseDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, UUID> {
    List<CourseDocument> findAllByOrderByUploadedAtDesc();

    List<CourseDocument> findByUploadedByOrderByUploadedAtDesc(UUID uploadedBy);

    List<CourseDocument> findByWorkspaceIdOrderByUploadedAtDesc(UUID workspaceId);

    List<CourseDocument> findByWorkspaceIdAndUploadedByOrderByUploadedAtDesc(UUID workspaceId, UUID uploadedBy);
    List<CourseDocument> findByCourseIdOrderByUploadedAtDesc(UUID courseId);
    List<CourseDocument> findByCourseIdAndProcessingStatusOrderByUploadedAtDesc(UUID courseId, String processingStatus);
    List<CourseDocument> findByCourseIdInAndProcessingStatus(List<UUID> courseIds, String processingStatus);
    boolean existsByCourseIdAndProcessingStatus(UUID courseId, String processingStatus);
    List<CourseDocument> findByReviewStatusOrderBySubmittedAtAsc(String reviewStatus);
    long countByUploadedBy(UUID uploadedBy);
    long countByProcessingStatus(String processingStatus);
    long countByReviewStatus(String reviewStatus);
    long countByCloudinaryPreviewUrlIsNullAndFileTypeNot(String fileType);
    List<CourseDocument> findTop5ByOrderByUploadedAtDesc();
    List<CourseDocument> findByUploadedAtAfterOrderByUploadedAtAsc(LocalDateTime uploadedAt);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from CourseDocument d where d.uploadedBy = :uploadedBy")
    Long sumFileSizeByUploadedBy(UUID uploadedBy);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from CourseDocument d")
    Long sumFileSize();
}
