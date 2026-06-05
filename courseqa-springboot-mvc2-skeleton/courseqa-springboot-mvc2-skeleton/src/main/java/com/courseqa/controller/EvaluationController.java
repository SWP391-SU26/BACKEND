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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * EvaluationController - API endpoints cho evaluation functionality
 * - Quản lý datasets, questions, experiments, results
 */
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class EvaluationController {

    private final EvaluationService evaluationService;

    /**
     * GET /api/evaluation/datasets
     * Lấy danh sách tất cả datasets
     *
     * @return ResponseEntity<ApiResponse<List<EvaluationDataset>>>
     */
    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<EvaluationDataset>>> listDatasets() {
        log.info("GET /api/evaluation/datasets");

        List<EvaluationDataset> datasets = evaluationService.listDatasets();

        return ResponseEntity.ok(ApiResponse.ok(datasets));
    }

    /**
     * POST /api/evaluation/datasets
     * Tạo evaluation dataset mới
     *
     * @param request CreateDatasetRequest: {datasetName, courseId, workspaceId, createdBy}
     * @return ResponseEntity<ApiResponse<EvaluationDataset>>
     */
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

    /**
     * POST /api/evaluation/questions
     * Thêm câu hỏi vào dataset
     *
     * @param request AddQuestionRequest: {datasetId, questionText, groundTruthAnswer}
     * @return ResponseEntity<ApiResponse<EvaluationQuestion>>
     */
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

    /**
     * GET /api/evaluation/datasets/{datasetId}/questions
     * Lấy danh sách câu hỏi trong dataset
     *
     * @param datasetId ID của dataset
     * @return ResponseEntity<ApiResponse<List<EvaluationQuestion>>>
     */
    @GetMapping("/datasets/{datasetId}/questions")
    public ResponseEntity<ApiResponse<List<EvaluationQuestion>>> getQuestions(
            @PathVariable UUID datasetId) {
        log.info("GET /api/evaluation/datasets/{}/questions", datasetId);

        List<EvaluationQuestion> questions = evaluationService.getQuestions(datasetId);

        return ResponseEntity.ok(ApiResponse.ok(questions));
    }

    /**
     * POST /api/evaluation/experiments
     * Tạo experiment record mới
     *
     * @param request CreateExperimentRequest: {experimentName, experimentType, configJson, createdBy}
     * @return ResponseEntity<ApiResponse<Experiment>>
     */
    @PostMapping("/experiments")
    public ResponseEntity<ApiResponse<Experiment>> createExperiment(
            @Valid @RequestBody CreateExperimentRequest request) {
        log.info("POST /api/evaluation/experiments - name: {}", request.getExperimentName());

        Experiment experiment = evaluationService.createExperiment(
                request.getExperimentName(),
                request.getExperimentType(),
                request.getConfigJson(),
                request.getCreatedBy()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(experiment));
    }

    /**
     * GET /api/evaluation/experiments
     * Lấy danh sách tất cả experiments
     *
     * @return ResponseEntity<ApiResponse<List<Experiment>>>
     */
    @GetMapping("/experiments")
    public ResponseEntity<ApiResponse<List<Experiment>>> listExperiments() {
        log.info("GET /api/evaluation/experiments");

        List<Experiment> experiments = evaluationService.listExperiments();

        return ResponseEntity.ok(ApiResponse.ok(experiments));
    }

    /**
     * POST /api/evaluation/experiments/{experimentId}/run
     * Chạy benchmark cho experiment
     * TODO: Implement sau khi có API contract từ TV6
     *
     * @param experimentId ID của experiment
     * @return ResponseEntity<ApiResponse<String>>
     */
    @PostMapping("/experiments/{experimentId}/run")
    public ResponseEntity<ApiResponse<String>> runBenchmark(@PathVariable UUID experimentId) {
        log.info("POST /api/evaluation/experiments/{}/run", experimentId);

        // TODO: Implement runBenchmark logic:
        // 1. Gọi evaluationService.runBenchmark(experimentId)
        // 2. TODO sẽ implement khi TV6 confirm API contract

        throw new UnsupportedOperationException("runBenchmark not implemented yet - waiting for TV6 API contract");
    }

    /**
     * GET /api/evaluation/experiments/{experimentId}/results
     * Lấy kết quả của experiment
     *
     * @param experimentId ID của experiment
     * @return ResponseEntity<ApiResponse<List<ExperimentResult>>>
     */
    @GetMapping("/experiments/{experimentId}/results")
    public ResponseEntity<ApiResponse<List<ExperimentResult>>> getResults(
            @PathVariable UUID experimentId) {
        log.info("GET /api/evaluation/experiments/{}/results", experimentId);

        List<ExperimentResult> results = evaluationService.getResults(experimentId);

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createDataset
     */
    public static class CreateDatasetRequest {
        @NotBlank(message = "datasetName is required and cannot be empty")
        private String datasetName;

        @NotNull(message = "courseId is required")
        private UUID courseId;

        @NotNull(message = "workspaceId is required")
        private UUID workspaceId;

        @NotNull(message = "createdBy is required")
        private UUID createdBy;

        public CreateDatasetRequest() {}

        public CreateDatasetRequest(String datasetName, UUID courseId, UUID workspaceId, UUID createdBy) {
            this.datasetName = datasetName;
            this.courseId = courseId;
            this.workspaceId = workspaceId;
            this.createdBy = createdBy;
        }

        public String getDatasetName() { return datasetName; }
        public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

        public UUID getCourseId() { return courseId; }
        public void setCourseId(UUID courseId) { this.courseId = courseId; }

        public UUID getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

        public UUID getCreatedBy() { return createdBy; }
        public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    }

    /**
     * Request DTO cho addQuestion
     */
    public static class AddQuestionRequest {
        @NotNull(message = "datasetId is required")
        private UUID datasetId;

        @NotBlank(message = "questionText is required and cannot be empty")
        private String questionText;

        @NotBlank(message = "groundTruthAnswer is required and cannot be empty")
        private String groundTruthAnswer;

        public AddQuestionRequest() {}

        public AddQuestionRequest(UUID datasetId, String questionText, String groundTruthAnswer) {
            this.datasetId = datasetId;
            this.questionText = questionText;
            this.groundTruthAnswer = groundTruthAnswer;
        }

        public UUID getDatasetId() { return datasetId; }
        public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

        public String getQuestionText() { return questionText; }
        public void setQuestionText(String questionText) { this.questionText = questionText; }

        public String getGroundTruthAnswer() { return groundTruthAnswer; }
        public void setGroundTruthAnswer(String groundTruthAnswer) { this.groundTruthAnswer = groundTruthAnswer; }
    }

    /**
     * Request DTO cho createExperiment
     */
    public static class CreateExperimentRequest {
        @NotBlank(message = "experimentName is required and cannot be empty")
        private String experimentName;

        @NotBlank(message = "experimentType is required and cannot be empty")
        private String experimentType;

        @NotBlank(message = "configJson is required and cannot be empty")
        private String configJson;

        @NotNull(message = "createdBy is required")
        private UUID createdBy;

        public CreateExperimentRequest() {}

        public CreateExperimentRequest(String experimentName, String experimentType, String configJson, UUID createdBy) {
            this.experimentName = experimentName;
            this.experimentType = experimentType;
            this.configJson = configJson;
            this.createdBy = createdBy;
        }

        public String getExperimentName() { return experimentName; }
        public void setExperimentName(String experimentName) { this.experimentName = experimentName; }

        public String getExperimentType() { return experimentType; }
        public void setExperimentType(String experimentType) { this.experimentType = experimentType; }

        public String getConfigJson() { return configJson; }
        public void setConfigJson(String configJson) { this.configJson = configJson; }

        public UUID getCreatedBy() { return createdBy; }
        public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    }
}
