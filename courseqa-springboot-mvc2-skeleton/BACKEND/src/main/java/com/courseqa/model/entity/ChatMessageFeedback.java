package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_message_feedback")
public class ChatMessageFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id")
    private UUID feedbackId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "helpful", nullable = false)
    private boolean helpful;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 32)
    private FeedbackReason reasonCode;

    @Column(name = "comment", columnDefinition = "NVARCHAR(1000)")
    private String comment;

    /** Set once the feedback has been turned into an evaluation question. */
    @Column(name = "promoted_question_id")
    private UUID promotedQuestionId;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * UTC, to match the SYSUTCDATETIME() defaults the migration puts on these
     * columns. Server-local time here would silently mix two clocks in one table.
     */
    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = nowUtc();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = nowUtc();
    }

    public ChatMessageFeedback() { }

    public ChatMessageFeedback(UUID messageId, UUID userId, boolean helpful,
                               FeedbackReason reasonCode, String comment) {
        this.messageId = messageId;
        this.userId = userId;
        this.helpful = helpful;
        this.reasonCode = reasonCode;
        this.comment = comment;
    }

    public UUID getFeedbackId() { return feedbackId; }
    public UUID getMessageId() { return messageId; }
    public UUID getUserId() { return userId; }
    public boolean isHelpful() { return helpful; }
    public FeedbackReason getReasonCode() { return reasonCode; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public UUID getPromotedQuestionId() { return promotedQuestionId; }
    public LocalDateTime getPromotedAt() { return promotedAt; }

    public void markPromoted(UUID questionId) {
        this.promotedQuestionId = questionId;
        this.promotedAt = nowUtc();
    }

    public void setHelpful(boolean helpful) { this.helpful = helpful; }
    public void setReasonCode(FeedbackReason reasonCode) { this.reasonCode = reasonCode; }
    public void setComment(String comment) { this.comment = comment; }
}