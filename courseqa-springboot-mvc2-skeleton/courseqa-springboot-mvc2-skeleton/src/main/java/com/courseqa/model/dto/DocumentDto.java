package com.courseqa.model.dto;

import java.util.UUID;

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
        public String documentTitle;
        public String originalFilename;
        public String fileType;
        public String processingStatus;
        public Integer totalPages;
        public String errorMessage;
    }

    public static class PageResponse {
        public UUID pageId;
        public UUID documentId;
        public Integer pageNumber;
        public String cleanedText;
        public Integer wordCount;
        public Integer charCount;
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
    }
}
