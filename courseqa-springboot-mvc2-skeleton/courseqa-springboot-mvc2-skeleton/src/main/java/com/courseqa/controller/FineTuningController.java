package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.entity.Experiment;
import com.courseqa.service.FineTuningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FineTuningController - API endpoints cho fine-tuning functionality
 * - Export JSONL training data
 * - List experiment files
 * - Manage fine-tuning experiments
 */
@RestController
@RequestMapping("/api/fine-tuning")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class FineTuningController {

    private final FineTuningService fineTuningService;

    /**
     * POST /api/fine-tuning/export-jsonl/{datasetId}
     * Export dataset thành JSONL file download
     * Format: {"prompt": "question", "completion": "answer"}
     *
     * @param datasetId ID của dataset
     * @return ResponseEntity<Resource> - file download (Content-Disposition: attachment)
     */
    @PostMapping("/export-jsonl/{datasetId}")
    public ResponseEntity<Resource> exportJsonl(@PathVariable Long datasetId) {
        logger.info("POST /api/fine-tuning/export-jsonl/{}", datasetId);

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
        logger.info("GET /api/fine-tuning/files");

        List<String> files = fineTuningService.listExperimentFiles();

        return ResponseEntity.ok(ApiResponse.success(files));
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
            @Valid @RequestBody CreateExperimentRecordRequest request) {
        logger.info("POST /api/fine-tuning/experiments - name: {}, researcherId: {}", 
            request.getName(), request.getResearcherId());

        Experiment experiment = fineTuningService.createExperimentRecord(
                request.getName(),
                request.getResearcherId(),
                request.getConfigJson()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(experiment));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createExperimentRecord
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateExperimentRecordRequest {
        @NotBlank(message = "name is required and cannot be empty")
        private String name;

        @NotNull(message = "researcherId is required")
        private Long researcherId;

        @NotBlank(message = "configJson is required and cannot be empty")
        private String configJson;
    }
}
