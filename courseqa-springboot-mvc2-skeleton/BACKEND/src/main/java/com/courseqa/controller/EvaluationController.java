package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.ExperimentResult;
import com.courseqa.service.EvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/evaluation")
@CrossOrigin
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<EvaluationDataset>>> listDatasets() {
        log.info("GET /api/evaluation/datasets");
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.listDatasets()));
    }

    @PostMapping("/datasets")
    public ResponseEntity<ApiResponse<EvaluationDataset>> createDataset(
            @Valid @RequestBody CreateDatasetRequest request) {
        log.info("POST /api/evaluation/datasets - name: {}", request.getDatasetName());

        EvaluationDataset dataset = evaluationService.createDataset(
                request.getDatasetName(),
                request.getCourseId(),
                request.getWorkspaceId(),
                request.getCreatedBy()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dataset));
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<EvaluationQuestion>> addQuestion(
            @Valid @RequestBody AddQuestionRequest request) {
        log.info("POST /api/evaluation/questions - datasetId: {}", request.getDatasetId());

        EvaluationQuestion question = evaluationService.addQuestion(
                request.getDatasetId(),
                request.getQuestionText(),
                request.getGroundTruthAnswer()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(question));
    }

    @GetMapping("/datasets/{datasetId}/questions")
    public ResponseEntity<ApiResponse<List<EvaluationQuestion>>> getQuestions(
            @PathVariable UUID datasetId) {
        log.info("GET /api/evaluation/datasets/{}/questions", datasetId);
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getQuestions(datasetId)));
    }

    @PostMapping("/experiments")
    public ResponseEntity<ApiResponse<Experiment>> createExperiment(
            @Valid @RequestBody CreateExperimentRequest request) {
        log.info("POST /api/evaluation/experiments - name: {}", request.getExperimentName());

        Experiment experiment = evaluationService.createExperiment(
                request.getDatasetId(),
                request.getExperimentName(),
                request.getExperimentType(),
                request.getLlmModel(),
                request.getConfigJson(),
                request.getCreatedBy()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(experiment));
    }

    @GetMapping("/experiments")
    public ResponseEntity<ApiResponse<List<Experiment>>> listExperiments() {
        log.info("GET /api/evaluation/experiments");
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.listExperiments()));
    }

    @PostMapping("/experiments/{experimentId}/run")
    public ResponseEntity<ApiResponse<Experiment>> runBenchmark(@PathVariable UUID experimentId) {
        log.info("POST /api/evaluation/experiments/{}/run", experimentId);
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.runBenchmark(experimentId)));
    }

    @GetMapping("/experiments/{experimentId}/results")
    public ResponseEntity<ApiResponse<List<ExperimentResult>>> getResults(
            @PathVariable UUID experimentId) {
        log.info("GET /api/evaluation/experiments/{}/results", experimentId);
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getResults(experimentId)));
    }

    public static class CreateDatasetRequest {
        @NotBlank(message = "datasetName is required and cannot be empty")
        private String datasetName;

        @NotNull(message = "courseId is required")
        private UUID courseId;

        @NotNull(message = "workspaceId is required")
        private UUID workspaceId;

        @NotNull(message = "createdBy is required")
        private UUID createdBy;

        public String getDatasetName() {
            return datasetName;
        }

        public void setDatasetName(String datasetName) {
            this.datasetName = datasetName;
        }

        public UUID getCourseId() {
            return courseId;
        }

        public void setCourseId(UUID courseId) {
            this.courseId = courseId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(UUID workspaceId) {
            this.workspaceId = workspaceId;
        }

        public UUID getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(UUID createdBy) {
            this.createdBy = createdBy;
        }
    }

    public static class AddQuestionRequest {
        @NotNull(message = "datasetId is required")
        private UUID datasetId;

        @NotBlank(message = "questionText is required and cannot be empty")
        private String questionText;

        @NotBlank(message = "groundTruthAnswer is required and cannot be empty")
        private String groundTruthAnswer;

        public UUID getDatasetId() {
            return datasetId;
        }

        public void setDatasetId(UUID datasetId) {
            this.datasetId = datasetId;
        }

        public String getQuestionText() {
            return questionText;
        }

        public void setQuestionText(String questionText) {
            this.questionText = questionText;
        }

        public String getGroundTruthAnswer() {
            return groundTruthAnswer;
        }

        public void setGroundTruthAnswer(String groundTruthAnswer) {
            this.groundTruthAnswer = groundTruthAnswer;
        }
    }

    public static class CreateExperimentRequest {
        @NotNull(message = "datasetId is required")
        private UUID datasetId;

        @NotBlank(message = "experimentName is required and cannot be empty")
        private String experimentName;

        @NotBlank(message = "experimentType is required and cannot be empty")
        private String experimentType;

        @NotBlank(message = "llmModel is required and cannot be empty")
        private String llmModel;

        @NotBlank(message = "configJson is required and cannot be empty")
        private String configJson;

        @NotNull(message = "createdBy is required")
        private UUID createdBy;

        public UUID getDatasetId() {
            return datasetId;
        }

        public void setDatasetId(UUID datasetId) {
            this.datasetId = datasetId;
        }

        public String getExperimentName() {
            return experimentName;
        }

        public void setExperimentName(String experimentName) {
            this.experimentName = experimentName;
        }

        public String getExperimentType() {
            return experimentType;
        }

        public void setExperimentType(String experimentType) {
            this.experimentType = experimentType;
        }

        public String getLlmModel() {
            return llmModel;
        }

        public void setLlmModel(String llmModel) {
            this.llmModel = llmModel;
        }

        public String getConfigJson() {
            return configJson;
        }

        public void setConfigJson(String configJson) {
            this.configJson = configJson;
        }

        public UUID getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(UUID createdBy) {
            this.createdBy = createdBy;
        }
    }
}
