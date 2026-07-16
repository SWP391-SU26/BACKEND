package com.courseqa.repository;

import com.courseqa.model.entity.EvaluationDatasetDocument;
import com.courseqa.model.entity.EvaluationDatasetDocumentId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationDatasetDocumentRepository
        extends JpaRepository<EvaluationDatasetDocument, EvaluationDatasetDocumentId> {
    List<EvaluationDatasetDocument> findByIdDatasetId(UUID datasetId);
    long countByIdDatasetId(UUID datasetId);
    void deleteByIdDatasetId(UUID datasetId);
}
