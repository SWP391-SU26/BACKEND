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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public void setHelpful(boolean helpful) { this.helpful = helpful; }
    public void setReasonCode(FeedbackReason reasonCode) { this.reasonCode = reasonCode; }
    public void setComment(String comment) { this.comment = comment; }
}