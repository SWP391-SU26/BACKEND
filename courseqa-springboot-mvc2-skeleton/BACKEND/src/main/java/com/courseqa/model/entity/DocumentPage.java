package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_pages")
public class DocumentPage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "page_id")
    private UUID pageId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "raw_text", columnDefinition = "NVARCHAR(MAX)")
    private String rawText;

    @Column(name = "cleaned_text", columnDefinition = "NVARCHAR(MAX)")
    private String cleanedText;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "char_count")
    private Integer charCount;

    @Column(name = "extraction_status")
    private String extractionStatus;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    public DocumentPage() { }

    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getCleanedText() { return cleanedText; }
    public void setCleanedText(String cleanedText) { this.cleanedText = cleanedText; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }

    public String getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(String extractionStatus) { this.extractionStatus = extractionStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }

}
