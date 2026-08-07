package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.entity.ProcessingJob;
import com.courseqa.service.DocumentProcessingService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin visibility into background document processing jobs (extraction, OCR,
 * chunking, embedding), so re-index/failures can be monitored and retried.
 */
@RestController
@RequestMapping("/api/admin/processing-jobs")
@CrossOrigin
public class ProcessingJobController {
    private final DocumentProcessingService documentProcessingService;

    public ProcessingJobController(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @GetMapping
    public ApiResponse<List<ProcessingJob>> listJobs() {
        return ApiResponse.ok(documentProcessingService.listJobs());
    }

    @GetMapping("/{jobId}")
    public ApiResponse<ProcessingJob> getJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(documentProcessingService.getJob(jobId));
    }
}
