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

    /** Opens a resumable upload: the client declares the file before sending it. */
    public static class ResumableUploadRequest {
        public String filename;
        public String mimeType;
        public Long totalBytes;
        public UUID workspaceId;
        public UUID courseId;
        public UUID chapterId;
    }

    /** Tells the client exactly which byte to send next after an interruption. */
    public static class ResumableUploadStatus {
        public UUID uploadId;
        public String status;
        public long receivedBytes;
        public long totalBytes;
        public long nextOffset;
        public int percent;
        public UUID documentId;

        public static ResumableUploadStatus fromEntity(com.courseqa.model.entity.UploadSession session) {
            ResumableUploadStatus status = new ResumableUploadStatus();
            status.uploadId = session.getUploadId();
            status.status = session.getStatus();
            status.receivedBytes = session.getReceivedBytes() == null ? 0 : session.getReceivedBytes();
            status.totalBytes = session.getTotalBytes() == null ? 0 : session.getTotalBytes();
            status.nextOffset = status.receivedBytes;
            status.percent = status.totalBytes <= 0
                    ? 0
                    : (int) Math.min(100, Math.round(status.receivedBytes * 100.0 / status.totalBytes));
            status.documentId = session.getDocumentId();
            return status;
        }
    }

    /**
     * Progress of the background processing job in a form the UI can render
     * directly. The percentage is derived here rather than in the browser so the
     * client never has to parse the internal step format.
     */
    public static class ProcessingStatusResponse {
        public UUID jobId;
        public UUID documentId;
        public String jobType;
        public String status;
        public String step;
        public Integer processedItems;
        public Integer totalItems;
        public int percent;
        public boolean finished;
        public boolean failed;
        public String errorMessage;
        public LocalDateTime startedAt;
        public LocalDateTime completedAt;
        public LocalDateTime updatedAt;

        /** Share of the bar reserved for embedding, the dominant phase. */
        private static final int EMBEDDING_FLOOR = 45;
        private static final int EMBEDDING_CEILING = 95;

        public static ProcessingStatusResponse fromEntity(
                com.courseqa.model.entity.ProcessingJob job) {
            ProcessingStatusResponse response = new ProcessingStatusResponse();
            response.jobId = job.getJobId();
            response.documentId = job.getDocumentId();
            response.jobType = job.getJobType();
            response.status = job.getStatus();
            response.errorMessage = job.getErrorMessage();
            response.startedAt = job.getStartedAt();
            response.completedAt = job.getCompletedAt();
            response.updatedAt = job.getUpdatedAt();

            String rawStep = job.getProgressStep() == null ? "" : job.getProgressStep().trim();
            String[] parts = rawStep.split("\\s+", 2);
            response.step = parts[0].isEmpty() ? null : parts[0];
            if (parts.length == 2 && parts[1].contains("/")) {
                String[] counts = parts[1].split("/", 2);
                response.processedItems = parseInteger(counts[0]);
                response.totalItems = parseInteger(counts[1]);
            }

            String status = response.status == null ? "" : response.status;
            response.failed = status.startsWith("FAILED");
            response.finished = response.failed || "COMPLETED".equals(status);
            response.percent = computePercent(response, status);
            return response;
        }

        private static int computePercent(ProcessingStatusResponse response, String status) {
            if (response.finished) {
                return 100;
            }
            if ("QUEUED".equals(status)) {
                return 5;
            }
            String step = response.step == null ? "" : response.step;
            return switch (step) {
                case "EXTRACTING" -> 15;
                case "OCR" -> 25;
                case "CHUNKING" -> 35;
                case "EMBEDDING" -> embeddingPercent(response);
                default -> 10;
            };
        }

        private static int embeddingPercent(ProcessingStatusResponse response) {
            if (response.processedItems == null || response.totalItems == null || response.totalItems <= 0) {
                return EMBEDDING_FLOOR;
            }
            double ratio = Math.min(1d, (double) response.processedItems / response.totalItems);
            return EMBEDDING_FLOOR + (int) Math.round(ratio * (EMBEDDING_CEILING - EMBEDDING_FLOOR));
        }

        private static Integer parseInteger(String value) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
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
        public String indexingStatus;
        public UUID indexedEmbeddingModelId;
        public String indexedModelVersion;
        public LocalDateTime indexedAt;
        public String indexError;
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
            response.indexingStatus = document.getIndexingStatus();
            response.indexedEmbeddingModelId = document.getIndexedEmbeddingModelId();
            response.indexedModelVersion = document.getIndexedModelVersion();
            response.indexedAt = document.getIndexedAt();
            response.indexError = document.getIndexError();
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

    public static class MoveWorkspaceRequest {
        public UUID workspaceId;
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
