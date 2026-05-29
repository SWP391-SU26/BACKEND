package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_datasets")
public class EvaluationDataset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "dataset_name")
    private String datasetName;

    @Column(name = "dataset_version")
    private String datasetVersion;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EvaluationDataset() { }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }


}
