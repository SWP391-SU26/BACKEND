package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "document_chapter_ranges")
public class DocumentChapterRange {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "document_chapter_range_id") private UUID documentChapterRangeId;
    @Column(name = "document_id") private UUID documentId;
    @Column(name = "chapter_id") private UUID chapterId;
    @Column(name = "page_start") private Integer pageStart;
    @Column(name = "page_end") private Integer pageEnd;
    public UUID getDocumentChapterRangeId(){return documentChapterRangeId;}
    public UUID getDocumentId(){return documentId;} public void setDocumentId(UUID v){documentId=v;}
    public UUID getChapterId(){return chapterId;} public void setChapterId(UUID v){chapterId=v;}
    public Integer getPageStart(){return pageStart;} public void setPageStart(Integer v){pageStart=v;}
    public Integer getPageEnd(){return pageEnd;} public void setPageEnd(Integer v){pageEnd=v;}
}
