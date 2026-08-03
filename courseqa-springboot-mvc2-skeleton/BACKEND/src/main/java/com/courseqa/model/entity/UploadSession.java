package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An upload in flight. Bytes are appended to a temp file as they arrive, so a
 * dropped connection can be resumed from the last confirmed offset instead of
 * restarting the whole transfer.
 */
@Entity
@Table(name = "upload_sessions")
public class UploadSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "upload_id")
    private UUID uploadId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "received_bytes")
    private Long receivedBytes;

    @Column(name = "temp_path")
    private String tempPath;

    @Column(name = "status")
    private String status;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Guards against two concurrent range uploads: both can pass the offset check
     * before either writes, so without a version the second write would silently
     * overwrite the first and leave the file corrupted.
     */
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    public UploadSession() { }

    public UUID getUploadId() { return uploadId; }
    public void setUploadId(UUID uploadId) { this.uploadId = uploadId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(Long totalBytes) { this.totalBytes = totalBytes; }

    public Long getReceivedBytes() { return receivedBytes; }
    public void setReceivedBytes(Long receivedBytes) { this.receivedBytes = receivedBytes; }

    public String getTempPath() { return tempPath; }
    public void setTempPath(String tempPath) { this.tempPath = tempPath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
}
