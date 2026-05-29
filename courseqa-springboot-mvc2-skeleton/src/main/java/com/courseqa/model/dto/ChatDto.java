package com.courseqa.model.dto;

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
}
