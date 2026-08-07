package com.courseqa.repository;

import com.courseqa.model.entity.EvaluationReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationReportRepository extends JpaRepository<EvaluationReport, UUID> {
    List<EvaluationReport> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);
    List<EvaluationReport> findAllByOrderByCreatedAtDesc();
}
