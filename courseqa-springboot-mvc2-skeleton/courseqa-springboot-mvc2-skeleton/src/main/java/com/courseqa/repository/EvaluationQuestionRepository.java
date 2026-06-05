package com.courseqa.repository;

import com.courseqa.model.entity.EvaluationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationQuestionRepository extends JpaRepository<EvaluationQuestion, UUID> {
    List<EvaluationQuestion> findByDatasetId(UUID datasetId);
}
