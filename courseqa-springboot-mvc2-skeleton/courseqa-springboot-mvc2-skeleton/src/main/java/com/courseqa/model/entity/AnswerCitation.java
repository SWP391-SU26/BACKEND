package com.courseqa.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.*;

import java.util.UUID;
@Entity
public class AnswerCitation {
 @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "citation_id")
    private UUID citationId;

    @Column(name = "assistant_message_id")
    private UUID assistantMessageId;

    @Column(name = "retrieval_result_id")
    private UUID retrievalResultId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "citation_order")
    private Integer citationOrder;

    @Column(name = "document_title")
    private String documentTitle;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "quote_text", columnDefinition = "NVARCHAR(MAX)")
    private String quoteText;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public AnswerCitation() { }

    public UUID getCitationId() { return citationId; }
    public void setCitationId(UUID citationId) { this.citationId = citationId; }

    public UUID getAssistantMessageId() { return assistantMessageId; }
    public void setAssistantMessageId(UUID assistantMessageId) { this.assistantMessageId = assistantMessageId; }

    public UUID getRetrievalResultId() { return retrievalResultId; }
    public void setRetrievalResultId(UUID retrievalResultId) { this.retrievalResultId = retrievalResultId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public Integer getCitationOrder() { return citationOrder; }
    public void setCitationOrder(Integer citationOrder) { this.citationOrder = citationOrder; }

    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }

    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }

    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }

    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
