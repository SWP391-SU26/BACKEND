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

    @Column(name = "latency_ms")
    private Integer latencyMs;

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

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

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

}
