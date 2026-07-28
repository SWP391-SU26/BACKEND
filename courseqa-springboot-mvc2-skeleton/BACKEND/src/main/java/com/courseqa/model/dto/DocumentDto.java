package com.courseqa.model.dto;

import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.DocumentPage;
import java.util.UUID;
import java.time.LocalDateTime;

// DTOs for upload document and document processing responses.
// TODO: Add request/response DTO classes here.

public class DocumentDto {
 public static class UploadDocumentRequest {
        public UUID workspaceId;
        public UUID courseId;
        public UUID chapterId;
        public UUID uploadedBy;
    }

    public static class DocumentResponse {
        public UUID documentId;
        public UUID workspaceId;
        public UUID courseId;
        public UUID chapterId;
        public UUID uploadedBy;
        public String documentTitle;
        public String originalFilename;
        public String fileType;
        public String processingStatus;
        public Integer totalPages;
        public String errorMessage;
        public String storageProvider;
        public String cloudinarySecureUrl;
        public String cloudinaryPreviewUrl;
        public Long fileSizeBytes;
        public String documentScope;
        public String reviewStatus;
        public UUID targetCourseId;
        public LocalDateTime submittedAt;
        public UUID reviewedBy;
        public LocalDateTime reviewedAt;
        public String rejectionReason;
        public LocalDateTime uploadedAt;
        public String uploaderName;
        public boolean canDelete;

        public static DocumentResponse fromEntity(CourseDocument document) {
            DocumentResponse response = new DocumentResponse();
            response.documentId = document.getDocumentId();
            response.workspaceId = document.getWorkspaceId();
            response.courseId = document.getCourseId();
            response.chapterId = document.getChapterId();
            response.uploadedBy = document.getUploadedBy();
            response.documentTitle = document.getDocumentTitle();
            response.originalFilename = document.getOriginalFilename();
            response.fileType = document.getFileType();
            response.processingStatus = document.getProcessingStatus();
            response.totalPages = document.getTotalPages();
            response.errorMessage = document.getErrorMessage();
            response.storageProvider = document.getStorageProvider();
            response.cloudinarySecureUrl = document.getCloudinarySecureUrl();
            response.cloudinaryPreviewUrl = document.getCloudinaryPreviewUrl();
            response.fileSizeBytes = document.getFileSizeBytes();
            response.documentScope = document.getDocumentScope();
            response.reviewStatus = document.getReviewStatus();
            response.targetCourseId = document.getTargetCourseId();
            response.submittedAt = document.getSubmittedAt();
            response.reviewedBy = document.getReviewedBy();
            response.reviewedAt = document.getReviewedAt();
            response.rejectionReason = document.getRejectionReason();
            response.uploadedAt = document.getUploadedAt();
            return response;
        }
    }

    public static class SubmissionRequest {
        public UUID courseId;
    }

    public static class ReviewRequest {
        public String status;
        public UUID courseId;
        public String rejectionReason;
    }

    public static class PageResponse {
        public UUID pageId;
        public UUID documentId;
        public Integer pageNumber;
        public String cleanedText;
        public Integer wordCount;
        public Integer charCount;

        public static PageResponse fromEntity(DocumentPage page) {
            PageResponse response = new PageResponse();
            response.pageId = page.getPageId();
            response.documentId = page.getDocumentId();
            response.pageNumber = page.getPageNumber();
            response.cleanedText = page.getCleanedText();
            response.wordCount = page.getWordCount();
            response.charCount = page.getCharCount();
            return response;
        }
    }

    public static class ChunkResponse {
        public UUID chunkId;
        public UUID documentId;
        public Integer chunkIndex;
        public String chunkStrategy;
        public String content;
        public Integer pageStart;
        public Integer pageEnd;
        public Integer tokenCount;

        public static ChunkResponse fromEntity(DocumentChunk chunk) {
            ChunkResponse response = new ChunkResponse();
            response.chunkId = chunk.getChunkId();
            response.documentId = chunk.getDocumentId();
            response.chunkIndex = chunk.getChunkIndex();
            response.chunkStrategy = chunk.getChunkStrategy();
            response.content = chunk.getContent();
            response.pageStart = chunk.getPageStart();
            response.pageEnd = chunk.getPageEnd();
            response.tokenCount = chunk.getTokenCount();
            return response;
        }
    }
}
