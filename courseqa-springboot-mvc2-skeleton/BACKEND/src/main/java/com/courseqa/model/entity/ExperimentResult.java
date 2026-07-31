package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "experiment_results")
public class ExperimentResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "experiment_result_id")
    private UUID experimentResultId;

    @Column(name = "experiment_id")
    private UUID experimentId;

    @Column(name = "evaluation_question_id")
    private UUID evaluationQuestionId;

    @Column(name = "generated_answer", columnDefinition = "NVARCHAR(MAX)")
    private String generatedAnswer;

    @Column(name = "retrieved_context_json", columnDefinition = "NVARCHAR(MAX)")
    private String retrievedContextJson;

    @Column(name = "citations_json", columnDefinition = "NVARCHAR(MAX)")
    private String citationsJson;

    @Column(name = "faithfulness")
    private Double faithfulness;

    @Column(name = "answer_relevance")
    private Double answerRelevance;

    @Column(name = "context_precision")
    private Double contextPrecision;

    @Column(name = "context_recall")
    private Double contextRecall;

    @Column(name = "answer_correctness")
    private Double answerCorrectness;

    @Column(name = "semantic_similarity")
    private Double semanticSimilarity;

    @Column(name = "provider_used")
    private String providerUsed;

    @Column(name = "base_model")
    private String baseModel;

    @Column(name = "adapter_version")
    private String adapterVersion;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "generation_mode")
    private String generationMode;

    @Column(name = "dataset_version")
    private String datasetVersion;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "metric_standard")
    private String metricStandard;

    @Column(name = "ragas_status")
    private String ragasStatus;

    @Column(name = "ragas_error", columnDefinition = "NVARCHAR(MAX)")
    private String ragasError;

    @Column(name = "ragas_evaluated_at")
    private LocalDateTime ragasEvaluatedAt;

    @Column(name = "judge_model")
    private String judgeModel;

    @Column(name = "evaluator_embedding")
    private String evaluatorEmbedding;

    @Column(name = "source_hit")
    private Boolean sourceHit;

    @Column(name = "page_hit")
    private Boolean pageHit;

    @Column(name = "refusal_correct")
    private Boolean refusalCorrect;

    @Column(name = "throughput_qps")
    private Double throughputQps;

    @Column(name = "peak_vram_bytes")
    private Long peakVramBytes;

    @Column(name = "model_verification_status")
    private String modelVerificationStatus;

    @Column(name = "quality_gate_passed")
    private Boolean qualityGatePassed;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "batch_latency_ms")
    private Integer batchLatencyMs;

    @Column(name = "effective_latency_ms")
    private Integer effectiveLatencyMs;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "cost")
    private BigDecimal cost;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private String questionText;

    @Transient
    private String groundTruthAnswer;

    public ExperimentResult() { }

    public UUID getExperimentResultId() { return experimentResultId; }
    public void setExperimentResultId(UUID experimentResultId) { this.experimentResultId = experimentResultId; }

    public UUID getExperimentId() { return experimentId; }
    public void setExperimentId(UUID experimentId) { this.experimentId = experimentId; }

    public UUID getEvaluationQuestionId() { return evaluationQuestionId; }
    public void setEvaluationQuestionId(UUID evaluationQuestionId) { this.evaluationQuestionId = evaluationQuestionId; }

    public String getGeneratedAnswer() { return generatedAnswer; }
    public void setGeneratedAnswer(String generatedAnswer) { this.generatedAnswer = generatedAnswer; }

    public String getRetrievedContextJson() { return retrievedContextJson; }
    public void setRetrievedContextJson(String retrievedContextJson) { this.retrievedContextJson = retrievedContextJson; }

    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }

    public Double getFaithfulness() { return faithfulness; }
    public void setFaithfulness(Double faithfulness) { this.faithfulness = faithfulness; }

    public Double getAnswerRelevance() { return answerRelevance; }
    public void setAnswerRelevance(Double answerRelevance) { this.answerRelevance = answerRelevance; }

    public Double getContextPrecision() { return contextPrecision; }
    public void setContextPrecision(Double contextPrecision) { this.contextPrecision = contextPrecision; }

    public Double getContextRecall() { return contextRecall; }
    public void setContextRecall(Double contextRecall) { this.contextRecall = contextRecall; }

    public Double getAnswerCorrectness() { return answerCorrectness; }
    public void setAnswerCorrectness(Double answerCorrectness) { this.answerCorrectness = answerCorrectness; }

    public Double getSemanticSimilarity() { return semanticSimilarity; }
    public void setSemanticSimilarity(Double semanticSimilarity) { this.semanticSimilarity = semanticSimilarity; }

    public String getProviderUsed() { return providerUsed; }
    public void setProviderUsed(String providerUsed) { this.providerUsed = providerUsed; }

    public String getBaseModel() { return baseModel; }
    public void setBaseModel(String baseModel) { this.baseModel = baseModel; }

    public String getAdapterVersion() { return adapterVersion; }
    public void setAdapterVersion(String adapterVersion) { this.adapterVersion = adapterVersion; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public String getGenerationMode() { return generationMode; }
    public void setGenerationMode(String generationMode) { this.generationMode = generationMode; }

    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public String getMetricStandard() { return metricStandard; }
    public void setMetricStandard(String metricStandard) { this.metricStandard = metricStandard; }

    public String getRagasStatus() { return ragasStatus; }
    public void setRagasStatus(String ragasStatus) { this.ragasStatus = ragasStatus; }

    public String getRagasError() { return ragasError; }
    public void setRagasError(String ragasError) { this.ragasError = ragasError; }

    public LocalDateTime getRagasEvaluatedAt() { return ragasEvaluatedAt; }
    public void setRagasEvaluatedAt(LocalDateTime ragasEvaluatedAt) { this.ragasEvaluatedAt = ragasEvaluatedAt; }

    public String getJudgeModel() { return judgeModel; }
    public void setJudgeModel(String judgeModel) { this.judgeModel = judgeModel; }

    public String getEvaluatorEmbedding() { return evaluatorEmbedding; }
    public void setEvaluatorEmbedding(String evaluatorEmbedding) { this.evaluatorEmbedding = evaluatorEmbedding; }

    public Boolean getSourceHit() { return sourceHit; }
    public void setSourceHit(Boolean sourceHit) { this.sourceHit = sourceHit; }

    public Boolean getPageHit() { return pageHit; }
    public void setPageHit(Boolean pageHit) { this.pageHit = pageHit; }

    public Boolean getRefusalCorrect() { return refusalCorrect; }
    public void setRefusalCorrect(Boolean refusalCorrect) { this.refusalCorrect = refusalCorrect; }

    public Double getThroughputQps() { return throughputQps; }
    public void setThroughputQps(Double throughputQps) { this.throughputQps = throughputQps; }

    public Long getPeakVramBytes() { return peakVramBytes; }
    public void setPeakVramBytes(Long peakVramBytes) { this.peakVramBytes = peakVramBytes; }

    public String getModelVerificationStatus() { return modelVerificationStatus; }
    public void setModelVerificationStatus(String modelVerificationStatus) {
        this.modelVerificationStatus = modelVerificationStatus;
    }

    public Boolean getQualityGatePassed() { return qualityGatePassed; }
    public void setQualityGatePassed(Boolean qualityGatePassed) {
        this.qualityGatePassed = qualityGatePassed;
    }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public Integer getBatchLatencyMs() { return batchLatencyMs; }
    public void setBatchLatencyMs(Integer batchLatencyMs) { this.batchLatencyMs = batchLatencyMs; }

    public Integer getEffectiveLatencyMs() { return effectiveLatencyMs; }
    public void setEffectiveLatencyMs(Integer effectiveLatencyMs) { this.effectiveLatencyMs = effectiveLatencyMs; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }

    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getGroundTruthAnswer() { return groundTruthAnswer; }
    public void setGroundTruthAnswer(String groundTruthAnswer) { this.groundTruthAnswer = groundTruthAnswer; }

}
