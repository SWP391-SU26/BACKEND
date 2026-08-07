package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.EvaluationDto.CreateReportRequest;
import com.courseqa.model.dto.LearningScopeDto;
import com.courseqa.model.dto.EvaluationDto.RunExperimentRequest;
import com.courseqa.model.dto.EvaluationDto.RunPairRequest;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.EvaluationDataset;
import com.courseqa.model.entity.EvaluationReport;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.ExperimentResult;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.EvaluationReportService;
import com.courseqa.service.EvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/evaluation")
@CrossOrigin
public class EvaluationController {
    private final EvaluationService evaluationService;
    private final EvaluationReportService reportService;

    @Autowired
    public EvaluationController(EvaluationService evaluationService, EvaluationReportService reportService) {
        this.evaluationService = evaluationService;
        this.reportService = reportService;
    }

    EvaluationController(EvaluationService evaluationService) {
        this(evaluationService, null);
    }

    @GetMapping("/scopes")
    public ResponseEntity<ApiResponse<List<LearningScopeDto.SemesterScope>>> scopes(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getScopes(principal.userId())));
    }

    @GetMapping("/datasets")
    public ResponseEntity<ApiResponse<List<EvaluationDataset>>> listDatasets() {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.listDatasets()));
    }

    @PostMapping("/datasets")
    public ResponseEntity<ApiResponse<EvaluationDataset>> createDataset(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateDatasetRequest request) {
        EvaluationDataset dataset = evaluationService.createDataset(request.datasetName, request.courseId,
                request.documentIds, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dataset));
    }

    @GetMapping("/datasets/{datasetId}/documents")
    public ResponseEntity<ApiResponse<List<CourseDocument>>> datasetDocuments(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getDatasetDocuments(datasetId)));
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<EvaluationQuestion>> addQuestion(@Valid @RequestBody AddQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(evaluationService.addQuestion(
                request.datasetId, request.questionText, request.groundTruthAnswer)));
    }

    @GetMapping("/datasets/{datasetId}/questions")
    public ResponseEntity<ApiResponse<List<EvaluationQuestion>>> getQuestions(@PathVariable UUID datasetId) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getQuestions(datasetId)));
    }

    @PostMapping(value = "/datasets/{datasetId}/questions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importQuestions(
            @PathVariable UUID datasetId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.importQuestions(datasetId, file)));
    }

    @PostMapping("/experiments")
    public ResponseEntity<ApiResponse<Experiment>> createExperiment(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateExperimentRequest request) {
        Experiment experiment = evaluationService.createExperiment(request.datasetId, request.experimentName,
                request.experimentType, request.llmModel, request.configJson, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(experiment));
    }

    @GetMapping("/experiments")
    public ResponseEntity<ApiResponse<List<Experiment>>> listExperiments() {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.listExperiments()));
    }

    @GetMapping("/experiments/{experimentId}")
    public ResponseEntity<ApiResponse<Experiment>> getExperiment(@PathVariable UUID experimentId) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getExperiment(experimentId)));
    }

    @PostMapping("/experiments/{experimentId}/run")
    public ResponseEntity<ApiResponse<Experiment>> runBenchmark(
            @PathVariable UUID experimentId,
            @RequestBody(required = false) RunExperimentRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(evaluationService.startBenchmark(
                        experimentId,
                        request != null && Boolean.TRUE.equals(request.allowUnverifiedModel))));
    }

    public ResponseEntity<ApiResponse<Experiment>> runBenchmark(UUID experimentId) {
        return runBenchmark(experimentId, null);
    }

    @PostMapping("/experiments/run-pair")
    public ResponseEntity<ApiResponse<Map<String, Experiment>>> runBenchmarkPair(
            @Valid @RequestBody RunPairRequest request) {
        if (request.ragExperimentId == null || request.fineTunedExperimentId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Both experiment ids are required.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
                evaluationService.startBenchmarkPair(
                        request.ragExperimentId,
                        request.fineTunedExperimentId,
                        Boolean.TRUE.equals(request.allowUnverifiedModel))));
    }

    @PostMapping("/experiments/{experimentId}/cancel")
    public ResponseEntity<ApiResponse<Experiment>> cancelBenchmark(@PathVariable UUID experimentId) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.cancelBenchmark(experimentId)));
    }

    @GetMapping("/experiments/{experimentId}/results")
    public ResponseEntity<ApiResponse<List<ExperimentResult>>> getResults(@PathVariable UUID experimentId) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.getResults(experimentId)));
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readiness(
            @RequestParam UUID datasetId, @RequestParam String experimentType) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.readiness(datasetId, experimentType)));
    }

    @GetMapping("/model-readiness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> modelReadiness() {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.modelReadiness()));
    }

    @GetMapping("/comparison")
    public ResponseEntity<ApiResponse<Map<String, Object>>> comparison(
            @RequestParam UUID datasetId,
            @RequestParam UUID ragExperimentId,
            @RequestParam UUID fineTunedExperimentId) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.comparison(datasetId, ragExperimentId,
                fineTunedExperimentId)));
    }

    @PostMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EvaluationReport>> createReport(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateReportRequest request) {
        if (request.datasetId == null || request.ragExperimentId == null || request.fineTunedExperimentId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "datasetId, ragExperimentId and fineTunedExperimentId are required.");
        }
        EvaluationReport report = reportService.createReport(request.datasetId, request.ragExperimentId,
                request.fineTunedExperimentId, request.language, request.title, principal.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(report));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<EvaluationReport>>> listReports(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.listReports(principal.userId())));
    }

    @GetMapping("/reports/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EvaluationReport>> getReport(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getReport(reportId, principal.userId())));
    }

    @GetMapping("/reports/{reportId}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadReport(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID reportId,
            @RequestParam String format) {
        EvaluationReportService.DownloadedReport download = reportService.download(reportId, format, principal.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.resource());
    }

    @PostMapping("/reports/{reportId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EvaluationReport>> retryReport(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID reportId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(reportService.retryReport(reportId, principal.userId())));
    }

    @DeleteMapping("/reports/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteReport(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID reportId) {
        reportService.deleteReport(reportId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "reportId", reportId)));
    }

    public static class CreateDatasetRequest {
        @NotBlank public String datasetName;
        @NotNull public UUID courseId;
        @NotEmpty public List<UUID> documentIds;
        public UUID workspaceId;
        public UUID createdBy;
    }

    public static class AddQuestionRequest {
        @NotNull public UUID datasetId;
        @NotBlank public String questionText;
        @NotBlank public String groundTruthAnswer;
    }

    public static class CreateExperimentRequest {
        @NotNull public UUID datasetId;
        @NotBlank public String experimentName;
        @NotBlank public String experimentType;
        @NotBlank public String llmModel;
        public String configJson = "{}";
        public UUID createdBy;
    }
}
