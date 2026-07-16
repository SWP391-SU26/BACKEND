package com.courseqa.model.dto;
import com.courseqa.model.entity.DocumentChapterSuggestion; import java.util.*;
public class ChapterSuggestionDto {
 public static class ReviewItem { public UUID suggestionId; public String title; public Integer pageStart; public Integer pageEnd; public Double confidence; public String status; public static ReviewItem from(DocumentChapterSuggestion e){ReviewItem r=new ReviewItem();r.suggestionId=e.getSuggestionId();r.title=e.getSuggestedTitle();r.pageStart=e.getPageStart();r.pageEnd=e.getPageEnd();r.confidence=e.getConfidence();r.status=e.getStatus();return r;} }
 public static class ConfirmRequest { public List<ReviewItem> chapters; }
}
