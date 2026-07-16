package com.courseqa.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EvaluationDatasetDocumentId implements Serializable {
    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "document_id")
    private UUID documentId;

    public EvaluationDatasetDocumentId() { }

    public EvaluationDatasetDocumentId(UUID datasetId, UUID documentId) {
        this.datasetId = datasetId;
        this.documentId = documentId;
    }

    public UUID getDatasetId() { return datasetId; }
    public UUID getDocumentId() { return documentId; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof EvaluationDatasetDocumentId other)) return false;
        return Objects.equals(datasetId, other.datasetId) && Objects.equals(documentId, other.documentId);
    }

    @Override
    public int hashCode() { return Objects.hash(datasetId, documentId); }
}
