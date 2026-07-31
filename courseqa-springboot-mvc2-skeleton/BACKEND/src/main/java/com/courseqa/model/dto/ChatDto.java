package com.courseqa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        public UUID citationId;
        public UUID assistantMessageId;
        public UUID retrievalResultId;
        public UUID chunkId;
        public UUID documentId;
        public String documentTitle;
        public Integer pageStart;
        public Integer pageEnd;
        public String quoteText;
        public Double retrievalScore;

        public CitationItem(String documentTitle, Integer pageStart, Integer pageEnd, String quoteText) {
            this.documentTitle = documentTitle;
            this.pageStart = pageStart;
            this.pageEnd = pageEnd;
            this.quoteText = quoteText;
        }

        public CitationItem(UUID citationId,
                            UUID assistantMessageId,
                            UUID retrievalResultId,
                            UUID chunkId,
                            UUID documentId,
                            String documentTitle,
                            Integer pageStart,
                            Integer pageEnd,
                            String quoteText) {
            this.citationId = citationId;
            this.assistantMessageId = assistantMessageId;
            this.retrievalResultId = retrievalResultId;
            this.chunkId = chunkId;
            this.documentId = documentId;
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
        public String answerMode;
        public String modelName;
        public String generationMode;
        public String providerUsed;
        public String baseModel;
        public String adapterVersion;
        public String embeddingModel;
        public String datasetVersion;
        public String promptVersion;
        public List<String> usedChunkIds;
        public Long peakVramBytes;
        public String groundingStatus;
        public String fallbackReason;
        public Double groundingScore;
        public Boolean repairAttempted;
        public Integer unsupportedSentenceCount;
        public String modelVerificationStatus;
        public Boolean qualityGatePassed;
        public Integer latencyMs;
        public String answerDepth;
        public String questionIntent;
        public List<ProcessingTraceItem> processingTrace;
        public UUID retrievalQueryId;
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

        public AskResponse(UUID chatSessionId,
                           UUID userMessageId,
                           UUID assistantMessageId,
                           String answer,
                           String answerMode,
                           String modelName,
                           UUID retrievalQueryId,
                           List<CitationItem> citations) {
            this.chatSessionId = chatSessionId;
            this.userMessageId = userMessageId;
            this.assistantMessageId = assistantMessageId;
            this.answer = answer;
            this.answerMode = answerMode;
            this.modelName = modelName;
            this.retrievalQueryId = retrievalQueryId;
            this.citations = citations;
        }

        public AskResponse(UUID chatSessionId, UUID userMessageId, UUID assistantMessageId,
                           String answer, String answerMode, String modelName, String generationMode,
                           UUID retrievalQueryId, List<CitationItem> citations) {
            this(chatSessionId, userMessageId, assistantMessageId, answer, answerMode, modelName,
                    retrievalQueryId, citations);
            this.generationMode = generationMode;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingTraceItem {
        private String step;
        private String status;
        private String messageKey;
        private Integer elapsedMs;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private UUID chatSessionId;
        private UUID userId;
        private UUID workspaceId;
        private UUID semesterId;
        private UUID courseId;
        private String scopeType;
        private List<UUID> documentIds;
        private String scopeLabel;
        private String sessionTitle;
        private Boolean isActive;
        private Boolean isPinned;
        private LocalDateTime pinnedAt;
        private LocalDateTime startedAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RenameSessionRequest {
        private String title;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PinSessionRequest {
        private Boolean pinned;
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
        private String answerDepth;
        private String questionIntent;
        private List<ProcessingTraceItem> processingTrace;
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
