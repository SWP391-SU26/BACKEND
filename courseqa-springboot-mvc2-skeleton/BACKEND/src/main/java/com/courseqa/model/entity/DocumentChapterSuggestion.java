package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "document_chapter_suggestions")
public class DocumentChapterSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "suggestion_id") private UUID suggestionId;
    @Column(name = "document_id") private UUID documentId;
    @Column(name = "suggested_title") private String suggestedTitle;
    @Column(name = "page_start") private Integer pageStart;
    @Column(name = "page_end") private Integer pageEnd;
    @Column(name = "confidence") private Double confidence;
    @Column(name = "status") private String status;
    public UUID getSuggestionId(){return suggestionId;} public UUID getDocumentId(){return documentId;} public void setDocumentId(UUID v){documentId=v;}
    public String getSuggestedTitle(){return suggestedTitle;} public void setSuggestedTitle(String v){suggestedTitle=v;}
    public Integer getPageStart(){return pageStart;} public void setPageStart(Integer v){pageStart=v;}
    public Integer getPageEnd(){return pageEnd;} public void setPageEnd(Integer v){pageEnd=v;}
    public Double getConfidence(){return confidence;} public void setConfidence(Double v){confidence=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
}
