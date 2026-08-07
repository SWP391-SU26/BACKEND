package com.courseqa.model.dto;

import com.courseqa.model.entity.ChatMessageFeedback;
import com.courseqa.model.entity.FeedbackReason;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transport shapes for FR-09 feedback. Kept separate from the entity so the
 * chat API never leaks the persistence model, and so the insights screen can
 * carry the question/answer text the entity itself does not hold.
 */
public final class FeedbackDto {

    private FeedbackDto() { }

    /** What a student sees for their own feedback on one answer. */
    public static class FeedbackResponse {
        public UUID feedbackId;
        public UUID messageId;
        public boolean helpful;
        public FeedbackReason reasonCode;
        public String comment;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;

        public static FeedbackResponse from(ChatMessageFeedback source) {
            FeedbackResponse dto = new FeedbackResponse();
            dto.feedbackId = source.getFeedbackId();
            dto.messageId = source.getMessageId();
            dto.helpful = source.isHelpful();
            dto.reasonCode = source.getReasonCode();
            dto.comment = source.getComment();
            dto.createdAt = source.getCreatedAt();
            dto.updatedAt = source.getUpdatedAt();
            return dto;
        }
    }

    /** Headline numbers for the researcher/admin insights screen. */
    public static class FeedbackStatsResponse {
        public long total;
        public long helpfulCount;
        public long notHelpfulCount;
        /** Share of feedback marked helpful, 0..1. Null when there is no feedback yet. */
        public Double helpfulRate;
        /** reason_code -> count, only for the not-helpful side. */
        public Map<String, Long> byReason;
        /** How many negative answers have already become evaluation questions. */
        public long promotedCount;
        public LocalDateTime from;
        public LocalDateTime to;
    }

    /**
     * One bad answer, with the question that produced it, ready to be reviewed
     * and promoted into an evaluation dataset.
     */
    public static class NegativeFeedbackItem {
        public UUID feedbackId;
        public UUID messageId;
        public UUID chatSessionId;
        public FeedbackReason reasonCode;
        public String comment;
        /** The user turn immediately before the answer; null if it cannot be found. */
        public String questionText;
        public String answerText;
        public String llmModel;
        public String questionIntent;
        public LocalDateTime createdAt;
        public UUID promotedQuestionId;
        public LocalDateTime promotedAt;
    }

    public static class NegativeFeedbackPage {
        public List<NegativeFeedbackItem> items;
        public long totalNegative;
    }

    /** Turns a bad answer into a benchmark case on an existing dataset. */
    public static class PromoteRequest {
        public UUID datasetId;
        /** Optional override; defaults to the original user question. */
        public String questionText;
        /** What the answer should have been. Required - a benchmark needs a target. */
        public String groundTruthAnswer;
    }

    public static class PromoteResponse {
        public UUID feedbackId;
        public UUID datasetId;
        public UUID evaluationQuestionId;
        public Integer questionNo;
        public String questionText;
    }
}
