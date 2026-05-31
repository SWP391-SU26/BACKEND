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
        logger.info("GET /api/evaluation/datasets");

        List<EvaluationDataset> datasets = evaluationService.listDatasets();

        return ResponseEntity.ok(ApiResponse.success(datasets));
    }

    /**
     * POST /api/evaluation/datasets
     * Tạo evaluation dataset mới
     *
     * @param request CreateDatasetRequest: {name, subjectId, createdBy}
     * @return ResponseEntity<ApiResponse<EvaluationDataset>>
     */
    @PostMapping("/datasets")
    public ResponseEntity<ApiResponse<EvaluationDataset>> createDataset(
            @Valid @RequestBody CreateDatasetRequest request) {
        logger.info("POST /api/evaluation/datasets - name: {}, subjectId: {}", request.getName(), request.getSubjectId());

        EvaluationDataset dataset = evaluationService.createDataset(
                request.getName(),
                request.getSubjectId(),
                request.getCreatedBy()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dataset));
    }

    /**
     * POST /api/evaluation/questions
     * Thêm câu hỏi vào dataset
     *
     * @param request AddQuestionRequest: {datasetId, question, groundTruth}
     * @return ResponseEntity<ApiResponse<EvaluationQuestion>>
     */
    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<EvaluationQuestion>> addQuestion(
            @Valid @RequestBody AddQuestionRequest request) {
        logger.info("POST /api/evaluation/questions - datasetId: {}", request.getDatasetId());

        EvaluationQuestion question = evaluationService.addQuestion(
                request.getDatasetId(),
                request.getQuestion(),
                request.getGroundTruth()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(question));
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
            @PathVariable Long datasetId) {
        logger.info("GET /api/evaluation/datasets/{}/questions", datasetId);

        List<EvaluationQuestion> questions = evaluationService.getQuestions(datasetId);

        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    /**
     * POST /api/evaluation/experiments
     * Tạo experiment record mới
     *
     * @param request CreateExperimentRequest: {name, researcherId, configJson}
     * @return ResponseEntity<ApiResponse<Experiment>>
     */
    @PostMapping("/experiments")
    public ResponseEntity<ApiResponse<Experiment>> createExperiment(
            @Valid @RequestBody CreateExperimentRequest request) {
        logger.info("POST /api/evaluation/experiments - name: {}, researcherId: {}", request.getName(), request.getResearcherId());

        Experiment experiment = evaluationService.createExperiment(
                request.getName(),
                request.getResearcherId(),
                request.getConfigJson()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(experiment));
    }

    /**
     * GET /api/evaluation/experiments
     * Lấy danh sách tất cả experiments
     *
     * @return ResponseEntity<ApiResponse<List<Experiment>>>
     */
    @GetMapping("/experiments")
    public ResponseEntity<ApiResponse<List<Experiment>>> listExperiments() {
        logger.info("GET /api/evaluation/experiments");

        List<Experiment> experiments = evaluationService.listExperiments();

        return ResponseEntity.ok(ApiResponse.success(experiments));
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
    public ResponseEntity<ApiResponse<String>> runBenchmark(@PathVariable Long experimentId) {
        logger.info("POST /api/evaluation/experiments/{}/run", experimentId);

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
            @PathVariable Long experimentId) {
        logger.info("GET /api/evaluation/experiments/{}/results", experimentId);

        List<ExperimentResult> results = evaluationService.getResults(experimentId);

        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createDataset
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateDatasetRequest {
        @NotBlank(message = "name is required and cannot be empty")
        private String name;

        @NotNull(message = "subjectId is required")
        private Long subjectId;

        @NotNull(message = "createdBy is required")
        private Long createdBy;
    }

    /**
     * Request DTO cho addQuestion
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AddQuestionRequest {
        @NotNull(message = "datasetId is required")
        private Long datasetId;

        @NotBlank(message = "question is required and cannot be empty")
        private String question;

        @NotBlank(message = "groundTruth is required and cannot be empty")
        private String groundTruth;
    }

    /**
     * Request DTO cho createExperiment
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateExperimentRequest {
        @NotBlank(message = "name is required and cannot be empty")
        private String name;

        @NotNull(message = "researcherId is required")
        private Long researcherId;

        @NotBlank(message = "configJson is required and cannot be empty")
        private String configJson;
    }
}
