package com.courseqa.repository;

import com.courseqa.model.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, UUID> {
    List<Experiment> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);
    List<Experiment> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId);
}
