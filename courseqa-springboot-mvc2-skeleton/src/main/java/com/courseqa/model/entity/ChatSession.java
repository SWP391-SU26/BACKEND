package com.courseqa.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;
// TODO: Add JPA annotations: @Entity, @Table
// TODO: Add fields matching database table
// TODO: Add constructors, getters, setters

@Entity
@Table(name = "chat_sessions")
public class ChatSession {
 @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "session_title")
    private String sessionTitle;

    @Column(name = "selected_embedding_model_id")
    private UUID selectedEmbeddingModelId;

    @Column(name = "selected_chunking_strategy")
    private String selectedChunkingStrategy;

    @Column(name = "system_prompt", columnDefinition = "NVARCHAR(MAX)")
    private String systemPrompt;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ChatSession() { }

    public UUID getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(UUID chatSessionId) { this.chatSessionId = chatSessionId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }

    public UUID getSelectedEmbeddingModelId() { return selectedEmbeddingModelId; }
    public void setSelectedEmbeddingModelId(UUID selectedEmbeddingModelId) { this.selectedEmbeddingModelId = selectedEmbeddingModelId; }

    public String getSelectedChunkingStrategy() { return selectedChunkingStrategy; }
    public void setSelectedChunkingStrategy(String selectedChunkingStrategy) { this.selectedChunkingStrategy = selectedChunkingStrategy; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}

