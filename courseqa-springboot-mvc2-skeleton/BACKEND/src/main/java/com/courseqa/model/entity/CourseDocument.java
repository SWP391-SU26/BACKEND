package com.courseqa.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import java.util.UUID;
// TODO: Add JPA annotations: @Entity, @Table
// TODO: Add fields matching database table
// TODO: Add constructors, getters, setters

@Entity
@Table(name = "course_documents")
public class CourseDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "document_title")
    private String documentTitle;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_path", columnDefinition = "NVARCHAR(MAX)")
    private String filePath;

    @Column(name = "storage_provider")
    private String storageProvider;

    @Column(name = "cloudinary_public_id")
    private String cloudinaryPublicId;

    @Column(name = "cloudinary_secure_url", columnDefinition = "NVARCHAR(MAX)")
    private String cloudinarySecureUrl;

    @Column(name = "cloudinary_preview_public_id")
    private String cloudinaryPreviewPublicId;

    @Column(name = "cloudinary_preview_url", columnDefinition = "NVARCHAR(MAX)")
    private String cloudinaryPreviewUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "processing_status")
    private String processingStatus;

    @Column(name = "indexing_status")
    private String indexingStatus;

    @Column(name = "indexed_embedding_model_id")
    private UUID indexedEmbeddingModelId;

    @Column(name = "indexed_model_version")
    private String indexedModelVersion;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @Column(name = "index_error", columnDefinition = "NVARCHAR(MAX)")
    private String indexError;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "language")
    private String language;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "document_scope")
    private String documentScope;

    @Column(name = "review_status")
    private String reviewStatus;

    @Column(name = "target_course_id")
    private UUID targetCourseId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "content_hash")
    private String contentHash;

    public CourseDocument() { }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }

    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public void setCloudinaryPublicId(String cloudinaryPublicId) { this.cloudinaryPublicId = cloudinaryPublicId; }

    public String getCloudinarySecureUrl() { return cloudinarySecureUrl; }
    public void setCloudinarySecureUrl(String cloudinarySecureUrl) { this.cloudinarySecureUrl = cloudinarySecureUrl; }

    public String getCloudinaryPreviewPublicId() { return cloudinaryPreviewPublicId; }
    public void setCloudinaryPreviewPublicId(String cloudinaryPreviewPublicId) { this.cloudinaryPreviewPublicId = cloudinaryPreviewPublicId; }

    public String getCloudinaryPreviewUrl() { return cloudinaryPreviewUrl; }
    public void setCloudinaryPreviewUrl(String cloudinaryPreviewUrl) { this.cloudinaryPreviewUrl = cloudinaryPreviewUrl; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public String getIndexingStatus() { return indexingStatus; }
    public void setIndexingStatus(String indexingStatus) { this.indexingStatus = indexingStatus; }

    public UUID getIndexedEmbeddingModelId() { return indexedEmbeddingModelId; }
    public void setIndexedEmbeddingModelId(UUID indexedEmbeddingModelId) { this.indexedEmbeddingModelId = indexedEmbeddingModelId; }

    public String getIndexedModelVersion() { return indexedModelVersion; }
    public void setIndexedModelVersion(String indexedModelVersion) { this.indexedModelVersion = indexedModelVersion; }

    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }

    public String getIndexError() { return indexError; }
    public void setIndexError(String indexError) { this.indexError = indexError; }

    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDocumentScope() { return documentScope; }
    public void setDocumentScope(String documentScope) { this.documentScope = documentScope; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public UUID getTargetCourseId() { return targetCourseId; }
    public void setTargetCourseId(UUID targetCourseId) { this.targetCourseId = targetCourseId; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

}
