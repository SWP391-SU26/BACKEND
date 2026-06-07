package com.courseqa.repository;

import com.courseqa.model.entity.RetrievalResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrievalResultRepository extends JpaRepository<RetrievalResult, UUID> {
    List<RetrievalResult> findByRetrievalQueryIdOrderByResultRankAsc(UUID retrievalQueryId);
}
