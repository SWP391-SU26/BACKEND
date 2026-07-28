package com.courseqa.repository;
import com.courseqa.model.entity.DocumentChapterRange; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentChapterRangeRepository extends JpaRepository<DocumentChapterRange,UUID>{List<DocumentChapterRange> findByDocumentIdOrderByPageStartAsc(UUID documentId);void deleteByDocumentId(UUID documentId);}
