package com.courseqa.repository;

import com.courseqa.model.entity.CourseDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseDocumentRepository extends JpaRepository<CourseDocument, UUID> {
    List<CourseDocument> findAllByOrderByUploadedAtDesc();

    Optional<CourseDocument> findFirstByUploadedByAndContentHash(UUID uploadedBy, String contentHash);

    List<CourseDocument> findByIndexingStatusInAndUpdatedAtBefore(
            List<String> indexingStatuses, LocalDateTime cutoff);

    List<CourseDocument> findByUploadedByOrderByUploadedAtDesc(UUID uploadedBy);

    List<CourseDocument> findByWorkspaceIdOrderByUploadedAtDesc(UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    List<CourseDocument> findByWorkspaceIdAndUploadedByOrderByUploadedAtDesc(UUID workspaceId, UUID uploadedBy);
    List<CourseDocument> findByCourseIdOrderByUploadedAtDesc(UUID courseId);
    List<CourseDocument> findByCourseIdAndProcessingStatusOrderByUploadedAtDesc(UUID courseId, String processingStatus);
    List<CourseDocument> findByCourseIdInAndProcessingStatus(List<UUID> courseIds, String processingStatus);
    boolean existsByCourseIdAndProcessingStatus(UUID courseId, String processingStatus);
    List<CourseDocument> findByCourseIdAndProcessingStatusAndIndexingStatusOrderByUploadedAtDesc(
            UUID courseId, String processingStatus, String indexingStatus);
    List<CourseDocument> findByCourseIdInAndProcessingStatusAndIndexingStatus(
            List<UUID> courseIds, String processingStatus, String indexingStatus);
    boolean existsByCourseIdAndProcessingStatusAndIndexingStatus(
            UUID courseId, String processingStatus, String indexingStatus);
    List<CourseDocument> findByReviewStatusOrderBySubmittedAtAsc(String reviewStatus);
    long countByUploadedBy(UUID uploadedBy);
    long countByUploadedByAndDocumentScope(UUID uploadedBy, String documentScope);
    long countByProcessingStatus(String processingStatus);
    long countByReviewStatus(String reviewStatus);
    long countByCloudinaryPreviewUrlIsNullAndFileTypeNot(String fileType);
    List<CourseDocument> findTop5ByOrderByUploadedAtDesc();
    List<CourseDocument> findByUploadedAtAfterOrderByUploadedAtAsc(LocalDateTime uploadedAt);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from CourseDocument d where d.uploadedBy = :uploadedBy")
    Long sumFileSizeByUploadedBy(UUID uploadedBy);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from CourseDocument d where d.uploadedBy = :uploadedBy and d.documentScope = :documentScope")
    Long sumFileSizeByUploadedByAndDocumentScope(UUID uploadedBy, String documentScope);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from CourseDocument d")
    Long sumFileSize();
}
