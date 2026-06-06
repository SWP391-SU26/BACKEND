package com.courseqa.model.dto;
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

    public static class QuestionResponse {
        public UUID evaluationQuestionId;
        public Integer questionNo;
        public String questionText;
        public String groundTruthAnswer;
        public String questionType;
        public String difficulty;
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
    }
}
