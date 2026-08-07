package com.courseqa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
// DTOs for dataset, question, experiment, benchmark results.
// TODO: Add request/response DTO classes here.

public class EvaluationDto {
 public static class CreateDatasetRequest {
        public UUID courseId;
        public UUID workspaceId;
        public String datasetName;
        public String datasetVersion;
        public String description;
        public UUID createdBy;
    }

    public static class CreateQuestionRequest {
        public UUID datasetId;
        public UUID courseId;
        public UUID chapterId;
        public Integer questionNo;
        public String questionText;
        public String groundTruthAnswer;
        public UUID expectedDocumentId;
        public Integer expectedPage;
        public String questionType;
        public String difficulty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddQuestionRequest {
        private UUID datasetId;
        private String questionText;
        private String groundTruthAnswer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateExperimentRequest {
        private UUID datasetId;
        private String experimentName;
        private String experimentType;
        private String llmModel;
        private String configJson;
        private UUID createdBy;
    }

    public static class RunExperimentRequest {
        public Boolean allowUnverifiedModel;
    }

    public static class RunPairRequest {
        public UUID ragExperimentId;
        public UUID fineTunedExperimentId;
        public Boolean allowUnverifiedModel;
    }

    public static class CreateReportRequest {
        public UUID datasetId;
        public UUID ragExperimentId;
        public UUID fineTunedExperimentId;
        public String language;
        public String title;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatasetResponse {
        private UUID datasetId;
        private UUID courseId;
        private UUID workspaceId;
        private String datasetName;
        private String datasetVersion;
        private String description;
        private UUID createdBy;
        private LocalDateTime createdAt;
    }

    public static class QuestionResponse {
        public UUID evaluationQuestionId;
        public Integer questionNo;
        public String questionText;
        public String groundTruthAnswer;
        public String questionType;
        public String difficulty;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentResponse {
        private UUID experimentId;
        private UUID datasetId;
        private UUID courseId;
        private UUID workspaceId;
        private String experimentName;
        private String experimentType;
        private String configJson;
        private String status;
        private UUID createdBy;
        private LocalDateTime createdAt;
    }

    public static class ExperimentResultResponse {
        public UUID experimentResultId;
        public UUID experimentId;
        public UUID evaluationQuestionId;
        public String generatedAnswer;
        public Double faithfulness;
        public Double answerRelevance;
        public Double contextPrecision;
        public Double contextRecall;
        public Double answerCorrectness;
        public Double semanticSimilarity;
        public Integer latencyMs;
        public BigDecimal cost;
    }
}
