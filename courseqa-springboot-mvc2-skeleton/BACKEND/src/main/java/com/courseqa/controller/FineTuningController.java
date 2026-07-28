package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.entity.Experiment;
import com.courseqa.service.FineTuningService;
import com.courseqa.service.EvaluationService;
import com.courseqa.security.JwtPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * FineTuningController - API endpoints cho fine-tuning functionality
 * - Export JSONL training data
 * - List experiment files
 * - Manage fine-tuning experiments
 */
@RestController
@RequestMapping("/api/fine-tuning")
@CrossOrigin
public class FineTuningController {

    private static final Logger log = LoggerFactory.getLogger(FineTuningController.class);

    private final FineTuningService fineTuningService;
    private final EvaluationService evaluationService;

    public FineTuningController(FineTuningService fineTuningService, EvaluationService evaluationService) {
        this.fineTuningService = fineTuningService;
        this.evaluationService = evaluationService;
    }

    /**
     * POST /api/fine-tuning/export-jsonl/{datasetId}
     * Export dataset thành JSONL file download
     * Format: {"prompt": "question", "completion": "answer"}
     *
     * @param datasetId ID của dataset
     * @return ResponseEntity<Resource> - file download (Content-Disposition: attachment)
     */
    @PostMapping("/export-jsonl/{datasetId}")
    public ResponseEntity<Resource> exportJsonl(@PathVariable UUID datasetId) {
        log.info("POST /api/fine-tuning/export-jsonl/{}", datasetId);

        return fineTuningService.exportJsonl(datasetId);
    }

    /**
     * GET /api/fine-tuning/files
     * Lấy danh sách tên file của tất cả experiment records
     *
     * @return ResponseEntity<ApiResponse<List<String>>>
     */
    @GetMapping("/files")
    public ResponseEntity<ApiResponse<List<String>>> listExperimentFiles() {
        log.info("GET /api/fine-tuning/files");

        List<String> files = fineTuningService.listExperimentFiles();

        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    /**
     * POST /api/fine-tuning/experiments
     * Tạo fine-tuning experiment record mới
     *
     * @param request CreateExperimentRecordRequest: {name, researcherId, configJson}
     * @return ResponseEntity<ApiResponse<Experiment>>
     */
    @PostMapping("/experiments")
    public ResponseEntity<ApiResponse<Experiment>> createExperimentRecord(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateExperimentRecordRequest request) {
        log.info("POST /api/fine-tuning/experiments - name: {}, researcherId: {}", 
            request.getName(), request.getResearcherId());

        Experiment experiment = evaluationService.createExperiment(request.getDatasetId(), request.getName(),
                "FINE_TUNED", request.getLlmModel(), request.getConfigJson(), principal.userId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(experiment));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createExperimentRecord
     */
    public static class CreateExperimentRecordRequest {
        @NotBlank(message = "name is required and cannot be empty")
        private String name;

        @NotNull(message = "datasetId is required")
        private UUID datasetId;

        private UUID researcherId;

        @NotBlank(message = "llmModel is required and cannot be empty")
        private String llmModel;

        @NotBlank(message = "configJson is required and cannot be empty")
        private String configJson;

        public CreateExperimentRecordRequest() {}

        public CreateExperimentRecordRequest(String name, UUID datasetId, UUID researcherId, String llmModel, String configJson) {
            this.name = name;
            this.datasetId = datasetId;
            this.researcherId = researcherId;
            this.llmModel = llmModel;
            this.configJson = configJson;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public UUID getDatasetId() {
            return datasetId;
        }

        public void setDatasetId(UUID datasetId) {
            this.datasetId = datasetId;
        }

        public UUID getResearcherId() {
            return researcherId;
        }

        public void setResearcherId(UUID researcherId) {
            this.researcherId = researcherId;
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
    }
}
