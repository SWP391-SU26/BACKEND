package com.courseqa.repository;
import com.courseqa.model.entity.DocumentChapterSuggestion; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentChapterSuggestionRepository extends JpaRepository<DocumentChapterSuggestion,UUID>{List<DocumentChapterSuggestion> findByDocumentIdOrderByPageStartAsc(UUID documentId);void deleteByDocumentId(UUID documentId);}
