package com.courseqa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// DTOs for chat session, ask request, answer response, citations.
// TODO: Add request/response DTO classes here.

public class ChatDto {
 public static class CreateSessionRequest {
        public UUID workspaceId;
        public UUID userId;
        public UUID courseId;
        public UUID chapterId;
        public UUID selectedEmbeddingModelId;
        public String title;
    }

    public static class AskRequest {
        public UUID userId;
        public String question;
        public Integer topK = 5;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AskQuestionRequest {
        private String question;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveNoteRequest {
        private UUID userId;
        private UUID workspaceId;
        private String noteTitle;
        private String noteContent;
    }

    public static class CitationItem {
        public String documentTitle;
        public Integer pageStart;
        public Integer pageEnd;
        public String quoteText;

        public CitationItem(String documentTitle, Integer pageStart, Integer pageEnd, String quoteText) {
            this.documentTitle = documentTitle;
            this.pageStart = pageStart;
            this.pageEnd = pageEnd;
            this.quoteText = quoteText;
        }
    }

    public static class AskResponse {
        public UUID chatSessionId;
        public UUID userMessageId;
        public UUID assistantMessageId;
        public String answer;
        public List<CitationItem> citations;

        public AskResponse(UUID chatSessionId,
                           UUID userMessageId,
                           UUID assistantMessageId,
                           String answer,
                           List<CitationItem> citations) {
            this.chatSessionId = chatSessionId;
            this.userMessageId = userMessageId;
            this.assistantMessageId = assistantMessageId;
            this.answer = answer;
            this.citations = citations;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private UUID chatSessionId;
        private UUID userId;
        private UUID workspaceId;
        private Boolean isActive;
        private LocalDateTime startedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageResponse {
        private UUID messageId;
        private UUID chatSessionId;
        private String senderRole;
        private String messageContent;
        private String llmModel;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private Integer latencyMs;
        private LocalDateTime createdAt;
        private List<CitationResponse> citations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationResponse {
        private UUID citationId;
        private UUID chunkId;
        private UUID documentId;
        private Integer pageStart;
        private Integer pageEnd;
        private String quoteText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteResponse {
        private UUID noteId;
        private UUID userId;
        private UUID workspaceId;
        private String noteTitle;
        private String noteContent;
        private LocalDateTime createdAt;
    }
}
