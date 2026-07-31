package com.courseqa.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper();
@Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @Column(name = "sender_role")
    private String senderRole;

    @Column(name = "message_content", columnDefinition = "NVARCHAR(MAX)")
    private String messageContent;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "answer_depth")
    private String answerDepth;

    @Column(name = "question_intent")
    private String questionIntent;

    @Column(name = "processing_trace_json", columnDefinition = "NVARCHAR(MAX)")
    private String processingTraceJson;

    @Column(name = "cost")
    private BigDecimal cost;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ChatMessage() { }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(UUID chatSessionId) { this.chatSessionId = chatSessionId; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getMessageContent() { return messageContent; }
    public void setMessageContent(String messageContent) { this.messageContent = messageContent; }

    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }

    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public String getAnswerDepth() { return answerDepth; }
    public void setAnswerDepth(String answerDepth) { this.answerDepth = answerDepth; }

    public String getQuestionIntent() { return questionIntent; }
    public void setQuestionIntent(String questionIntent) { this.questionIntent = questionIntent; }

    @JsonIgnore
    public String getProcessingTraceJson() { return processingTraceJson; }
    public void setProcessingTraceJson(String processingTraceJson) { this.processingTraceJson = processingTraceJson; }

    @JsonProperty("processingTrace")
    public List<Map<String, Object>> getProcessingTrace() {
        if (processingTraceJson == null || processingTraceJson.isBlank()) {
            return List.of();
        }
        try {
            return TRACE_MAPPER.readValue(
                    processingTraceJson,
                    new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
