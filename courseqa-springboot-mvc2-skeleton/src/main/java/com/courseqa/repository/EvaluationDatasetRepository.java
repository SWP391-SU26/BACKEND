package com.courseqa.repository;

import com.courseqa.model.entity.EvaluationDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EvaluationDatasetRepository extends JpaRepository<EvaluationDataset, UUID> {
}
