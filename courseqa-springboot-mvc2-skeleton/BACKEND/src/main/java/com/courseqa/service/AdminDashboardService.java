package com.courseqa.service;

import com.courseqa.model.dto.AdminDashboardDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.Experiment;
import com.courseqa.model.entity.RetrievalQuery;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.EmbeddingModelRepository;
import com.courseqa.repository.EvaluationDatasetRepository;
import com.courseqa.repository.ExperimentRepository;
import com.courseqa.repository.RetrievalQueryRepository;
import com.courseqa.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
    private final UserRepository users;
    private final CourseRepository courses;
    private final CourseWorkspaceRepository workspaces;
    private final CourseDocumentRepository documents;
    private final EvaluationDatasetRepository datasets;
    private final ExperimentRepository experiments;
    private final RetrievalQueryRepository retrievalQueries;
    private final EmbeddingModelRepository embeddingModels;

    public AdminDashboardService(
            UserRepository users,
            CourseRepository courses,
            CourseWorkspaceRepository workspaces,
            CourseDocumentRepository documents,
            EvaluationDatasetRepository datasets,
            ExperimentRepository experiments,
            RetrievalQueryRepository retrievalQueries,
            EmbeddingModelRepository embeddingModels) {
        this.users = users;
        this.courses = courses;
        this.workspaces = workspaces;
        this.documents = documents;
        this.datasets = datasets;
        this.experiments = experiments;
        this.retrievalQueries = retrievalQueries;
        this.embeddingModels = embeddingModels;
    }

    public AdminDashboardDto.SummaryResponse summary() {
        AdminDashboardDto.SummaryResponse response = new AdminDashboardDto.SummaryResponse();

        response.totals.users = users.count();
        response.totals.activeUsers = users.countByIsActiveTrue();
        response.totals.courses = courses.count();
        response.totals.activeCourses = courses.countByIsActiveTrue();
        response.totals.workspaces = workspaces.count();
        response.totals.activeWorkspaces = workspaces.countByIsActiveTrue();
        response.totals.documents = documents.count();
        response.totals.datasets = datasets.count();
        response.totals.experiments = experiments.count();
        response.totals.retrievalQueries = retrievalQueries.count();
        response.totals.embeddingModels = embeddingModels.count();
        response.totals.activeEmbeddingModels = embeddingModels.countByIsActiveTrue();

        response.documents.processed = documents.countByProcessingStatus("PROCESSED");
        response.documents.processing = documents.countByProcessingStatus("PROCESSING");
        response.documents.noText = documents.countByProcessingStatus("NO_TEXT");
        response.documents.failed = documents.countByProcessingStatus("FAILED");
        response.documents.pendingReview = documents.countByReviewStatus("PENDING");
        response.documents.approved = documents.countByReviewStatus("APPROVED");
        response.documents.rejected = documents.countByReviewStatus("REJECTED");
        response.documents.missingPreview = documents.countByCloudinaryPreviewUrlIsNullAndFileTypeNot("PDF");
        response.documents.totalStorageBytes = safeLong(documents.sumFileSize());

        response.experiments.pending = experiments.countByStatus("PENDING");
        response.experiments.queued = experiments.countByStatus("QUEUED");
        response.experiments.running = experiments.countByStatus("RUNNING");
        response.experiments.completed = experiments.countByStatus("COMPLETED");
        response.experiments.failed = experiments.countByStatus("FAILED");
        response.experiments.cancelled = experiments.countByStatus("CANCELLED");

        response.activity.recentDocuments = documents.findTop5ByOrderByUploadedAtDesc().stream()
                .map(this::toDocumentActivity)
                .toList();
        response.activity.recentExperiments = experiments.findTop5ByOrderByCreatedAtDesc().stream()
                .map(this::toExperimentActivity)
                .toList();
        response.activity.recentRetrievalQueries = retrievalQueries.findTop5ByOrderByCreatedAtDesc().stream()
                .map(this::toRetrievalActivity)
                .toList();

        return response;
    }

    public AdminDashboardDto.TimeseriesResponse timeseries(Integer requestedDays) {
        int days = Math.max(1, Math.min(requestedDays == null ? 14 : requestedDays, 90));
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();

        Map<LocalDate, AdminDashboardDto.TimeseriesPoint> points = new LinkedHashMap<>();
        for (int index = 0; index < days; index++) {
            LocalDate date = startDate.plusDays(index);
            AdminDashboardDto.TimeseriesPoint point = new AdminDashboardDto.TimeseriesPoint();
            point.date = date;
            points.put(date, point);
        }

        documents.findByUploadedAtAfterOrderByUploadedAtAsc(start).forEach(document -> {
            LocalDate date = toDate(document.getUploadedAt());
            AdminDashboardDto.TimeseriesPoint point = points.get(date);
            if (point != null) point.documentUploads++;
        });
        retrievalQueries.findByCreatedAtAfterOrderByCreatedAtAsc(start).forEach(query -> {
            LocalDate date = toDate(query.getCreatedAt());
            AdminDashboardDto.TimeseriesPoint point = points.get(date);
            if (point != null) point.retrievalQueries++;
        });
        experiments.findByCreatedAtAfterOrderByCreatedAtAsc(start).forEach(experiment -> {
            LocalDate date = toDate(experiment.getCreatedAt());
            AdminDashboardDto.TimeseriesPoint point = points.get(date);
            if (point != null) point.experimentsCreated++;
        });

        AdminDashboardDto.TimeseriesResponse response = new AdminDashboardDto.TimeseriesResponse();
        response.days = days;
        response.points.addAll(points.values());
        return response;
    }

    public AdminDashboardDto.HealthResponse health() {
        AdminDashboardDto.HealthResponse response = new AdminDashboardDto.HealthResponse();
        long failedDocuments = documents.countByProcessingStatus("FAILED");
        long pendingReviews = documents.countByReviewStatus("PENDING");
        long missingPreview = documents.countByCloudinaryPreviewUrlIsNullAndFileTypeNot("PDF");
        long failedExperiments = experiments.countByStatus("FAILED");
        long activeModels = embeddingModels.countByIsActiveTrue();

        response.items.add(healthItem("failedDocuments", "Failed documents", failedDocuments == 0 ? "OK" : "WARN",
                failedDocuments, failedDocuments == 0 ? "No failed document processing jobs." : "Some documents need admin attention."));
        response.items.add(healthItem("pendingReviews", "Pending reviews", pendingReviews == 0 ? "OK" : "INFO",
                pendingReviews, pendingReviews == 0 ? "Review queue is clear." : "Documents are waiting for admin review."));
        response.items.add(healthItem("missingPreview", "Missing previews", missingPreview == 0 ? "OK" : "WARN",
                missingPreview, missingPreview == 0 ? "All non-PDF documents have preview URLs." : "Some non-PDF documents may need re-upload or preview regeneration."));
        response.items.add(healthItem("failedExperiments", "Failed experiments", failedExperiments == 0 ? "OK" : "WARN",
                failedExperiments, failedExperiments == 0 ? "No failed evaluation experiments." : "Some experiments failed and should be inspected."));
        response.items.add(healthItem("activeEmbeddingModels", "Active embedding models", activeModels > 0 ? "OK" : "WARN",
                activeModels, activeModels > 0 ? "At least one embedding model is active." : "Create or activate an embedding model before preparing embeddings."));

        response.status = response.items.stream().anyMatch(item -> "WARN".equals(item.status)) ? "WARN" : "OK";
        return response;
    }

    private AdminDashboardDto.HealthItem healthItem(String key, String label, String status, long count, String message) {
        AdminDashboardDto.HealthItem item = new AdminDashboardDto.HealthItem();
        item.key = key;
        item.label = label;
        item.status = status;
        item.count = count;
        item.message = message;
        return item;
    }

    private AdminDashboardDto.DocumentActivity toDocumentActivity(CourseDocument document) {
        AdminDashboardDto.DocumentActivity item = new AdminDashboardDto.DocumentActivity();
        item.documentId = document.getDocumentId();
        item.courseId = document.getCourseId();
        item.workspaceId = document.getWorkspaceId();
        item.uploadedBy = document.getUploadedBy();
        item.documentTitle = document.getDocumentTitle();
        item.originalFilename = document.getOriginalFilename();
        item.fileType = document.getFileType();
        item.processingStatus = document.getProcessingStatus();
        item.reviewStatus = document.getReviewStatus();
        item.fileSizeBytes = document.getFileSizeBytes();
        item.uploadedAt = document.getUploadedAt();
        return item;
    }

    private AdminDashboardDto.ExperimentActivity toExperimentActivity(Experiment experiment) {
        AdminDashboardDto.ExperimentActivity item = new AdminDashboardDto.ExperimentActivity();
        item.experimentId = experiment.getExperimentId();
        item.datasetId = experiment.getDatasetId();
        item.experimentName = experiment.getExperimentName();
        item.experimentType = experiment.getExperimentType();
        item.llmModel = experiment.getLlmModel();
        item.status = experiment.getStatus();
        item.progress = experiment.getProgress();
        item.createdAt = experiment.getCreatedAt();
        item.updatedAt = experiment.getUpdatedAt();
        return item;
    }

    private AdminDashboardDto.RetrievalActivity toRetrievalActivity(RetrievalQuery query) {
        AdminDashboardDto.RetrievalActivity item = new AdminDashboardDto.RetrievalActivity();
        item.retrievalQueryId = query.getRetrievalQueryId();
        item.chatSessionId = query.getChatSessionId();
        item.workspaceId = query.getWorkspaceId();
        item.scopeType = query.getScopeType();
        item.queryText = query.getQueryText();
        item.isAnswerable = query.getIsAnswerable();
        item.latencyMs = query.getLatencyMs();
        item.createdAt = query.getCreatedAt();
        return item;
    }

    private LocalDate toDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
