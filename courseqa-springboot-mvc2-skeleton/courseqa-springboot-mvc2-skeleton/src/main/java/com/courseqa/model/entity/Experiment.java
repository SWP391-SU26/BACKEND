package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "experiments")
public class Experiment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "experiment_id")
    private UUID experimentId;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "experiment_name")
    private String experimentName;

    @Column(name = "experiment_type")
    private String experimentType;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "chunking_strategy")
    private String chunkingStrategy;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "fine_tuned_model_name")
    private String fineTunedModelName;

    @Column(name = "config_json", columnDefinition = "NVARCHAR(MAX)")
    private String configJson;

    @Column(name = "status")
    private String status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Experiment() { }

    public UUID getExperimentId() { return experimentId; }
    public void setExperimentId(UUID experimentId) { this.experimentId = experimentId; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { this.experimentName = experimentName; }

    public String getExperimentType() { return experimentType; }
    public void setExperimentType(String experimentType) { this.experimentType = experimentType; }

    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

    public UUID getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(UUID embeddingModelId) { this.embeddingModelId = embeddingModelId; }

    public String getChunkingStrategy() { return chunkingStrategy; }
    public void setChunkingStrategy(String chunkingStrategy) { this.chunkingStrategy = chunkingStrategy; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public String getFineTunedModelName() { return fineTunedModelName; }
    public void setFineTunedModelName(String fineTunedModelName) { this.fineTunedModelName = fineTunedModelName; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}
