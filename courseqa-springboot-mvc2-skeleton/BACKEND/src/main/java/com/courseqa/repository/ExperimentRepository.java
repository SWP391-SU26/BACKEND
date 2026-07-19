package com.courseqa.repository;

import com.courseqa.model.entity.Experiment;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {
    List<Experiment> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);
    List<Experiment> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId);
    long countByStatus(String status);
    List<Experiment> findTop5ByOrderByCreatedAtDesc();
    List<Experiment> findByCreatedAtAfterOrderByCreatedAtAsc(LocalDateTime createdAt);
}
