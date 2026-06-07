package com.courseqa.repository;

import com.courseqa.model.entity.DocumentPage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, UUID> {
    List<DocumentPage> findByDocumentIdOrderByPageNumberAsc(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
