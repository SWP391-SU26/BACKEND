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

    @Column(name = "similarity_threshold")
    private Double similarityThreshold;

    @Column(name = "embedding_model_version")
    private String embeddingModelVersion;

    @Column(name = "generation_model_version")
    private String generationModelVersion;

    @Column(name = "random_seed")
    private Integer randomSeed;

    @Column(name = "metric_standard")
    private String metricStandard;

    @Column(name = "evaluator_config_json", columnDefinition = "NVARCHAR(MAX)")
    private String evaluatorConfigJson;

    @Column(name = "frozen_config_hash")
    private String frozenConfigHash;

    @Column(name = "fine_tuned_model_name")
    private String fineTunedModelName;

    @Column(name = "config_json", columnDefinition = "NVARCHAR(MAX)")
    private String configJson;

    @Column(name = "status")
    private String status;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Column(name = "dataset_checksum")
    private String datasetChecksum;

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

    public Double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(Double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

    public String getEmbeddingModelVersion() { return embeddingModelVersion; }
    public void setEmbeddingModelVersion(String embeddingModelVersion) { this.embeddingModelVersion = embeddingModelVersion; }

    public String getGenerationModelVersion() { return generationModelVersion; }
    public void setGenerationModelVersion(String generationModelVersion) { this.generationModelVersion = generationModelVersion; }

    public Integer getRandomSeed() { return randomSeed; }
    public void setRandomSeed(Integer randomSeed) { this.randomSeed = randomSeed; }

    public String getMetricStandard() { return metricStandard; }
    public void setMetricStandard(String metricStandard) { this.metricStandard = metricStandard; }

    public String getEvaluatorConfigJson() { return evaluatorConfigJson; }
    public void setEvaluatorConfigJson(String evaluatorConfigJson) { this.evaluatorConfigJson = evaluatorConfigJson; }

    public String getFrozenConfigHash() { return frozenConfigHash; }
    public void setFrozenConfigHash(String frozenConfigHash) { this.frozenConfigHash = frozenConfigHash; }

    public String getFineTunedModelName() { return fineTunedModelName; }
    public void setFineTunedModelName(String fineTunedModelName) { this.fineTunedModelName = fineTunedModelName; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }

    public String getDatasetChecksum() { return datasetChecksum; }
    public void setDatasetChecksum(String datasetChecksum) { this.datasetChecksum = datasetChecksum; }

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
