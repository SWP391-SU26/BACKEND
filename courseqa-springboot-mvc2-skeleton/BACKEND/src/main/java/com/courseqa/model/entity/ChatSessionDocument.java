package com.courseqa.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "chat_session_documents")
public class ChatSessionDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chat_session_document_id")
    private UUID chatSessionDocumentId;

    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @Column(name = "document_id")
    private UUID documentId;

    public UUID getChatSessionDocumentId() { return chatSessionDocumentId; }
    public void setChatSessionDocumentId(UUID value) { chatSessionDocumentId = value; }
    public UUID getChatSessionId() { return chatSessionId; }
    public void setChatSessionId(UUID value) { chatSessionId = value; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID value) { documentId = value; }
}
