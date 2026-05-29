package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saved_notes")
public class SavedNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @Column(name = "note_title")
    private String noteTitle;

    @Column(name = "note_content", columnDefinition = "NVARCHAR(MAX)")
    private String noteContent;

    @Column(name = "note_type")
    private String noteType;

    @Column(name = "source_reference_json", columnDefinition = "NVARCHAR(MAX)")
    private String sourceReferenceJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SavedNote() { }

    public UUID getNoteId() { return noteId; }
    public void setNoteId(UUID noteId) { this.noteId = noteId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(UUID chatSessionId) { this.chatSessionId = chatSessionId; }

    public String getNoteTitle() { return noteTitle; }
    public void setNoteTitle(String noteTitle) { this.noteTitle = noteTitle; }

    public String getNoteContent() { return noteContent; }
    public void setNoteContent(String noteContent) { this.noteContent = noteContent; }

    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }

    public String getSourceReferenceJson() { return sourceReferenceJson; }
    public void setSourceReferenceJson(String sourceReferenceJson) { this.sourceReferenceJson = sourceReferenceJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}
