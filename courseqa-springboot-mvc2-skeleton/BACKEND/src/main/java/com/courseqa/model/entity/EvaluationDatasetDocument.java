package com.courseqa.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_dataset_documents")
public class EvaluationDatasetDocument {
    @EmbeddedId
    private EvaluationDatasetDocumentId id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public EvaluationDatasetDocument() { }

    public EvaluationDatasetDocument(UUID datasetId, UUID documentId) {
        this.id = new EvaluationDatasetDocumentId(datasetId, documentId);
        this.createdAt = LocalDateTime.now();
    }

    public EvaluationDatasetDocumentId getId() { return id; }
    public UUID getDatasetId() { return id == null ? null : id.getDatasetId(); }
    public UUID getDocumentId() { return id == null ? null : id.getDocumentId(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
