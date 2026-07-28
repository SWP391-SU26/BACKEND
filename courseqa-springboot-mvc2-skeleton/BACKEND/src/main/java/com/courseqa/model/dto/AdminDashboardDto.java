package com.courseqa.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminDashboardDto {
    public static class SummaryResponse {
        public Totals totals = new Totals();
        public DocumentStats documents = new DocumentStats();
        public ExperimentStats experiments = new ExperimentStats();
        public Activity activity = new Activity();
    }

    public static class Totals {
        public long users;
        public long activeUsers;
        public long courses;
        public long activeCourses;
        public long workspaces;
        public long activeWorkspaces;
        public long documents;
        public long datasets;
        public long experiments;
        public long retrievalQueries;
        public long embeddingModels;
        public long activeEmbeddingModels;
    }

    public static class DocumentStats {
        public long processed;
        public long processing;
        public long noText;
        public long failed;
        public long pendingReview;
        public long approved;
        public long rejected;
        public long missingPreview;
        public long totalStorageBytes;
    }

    public static class ExperimentStats {
        public long pending;
        public long queued;
        public long running;
        public long completed;
        public long failed;
        public long cancelled;
    }

    public static class Activity {
        public List<DocumentActivity> recentDocuments = new ArrayList<>();
        public List<ExperimentActivity> recentExperiments = new ArrayList<>();
        public List<RetrievalActivity> recentRetrievalQueries = new ArrayList<>();
    }

    public static class DocumentActivity {
        public UUID documentId;
        public UUID courseId;
        public UUID workspaceId;
        public UUID uploadedBy;
        public String documentTitle;
        public String originalFilename;
        public String fileType;
        public String processingStatus;
        public String reviewStatus;
        public Long fileSizeBytes;
        public LocalDateTime uploadedAt;
    }

    public static class ExperimentActivity {
        public UUID experimentId;
        public UUID datasetId;
        public String experimentName;
        public String experimentType;
        public String llmModel;
        public String status;
        public Integer progress;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    public static class RetrievalActivity {
        public UUID retrievalQueryId;
        public UUID chatSessionId;
        public UUID workspaceId;
        public String scopeType;
        public String queryText;
        public Boolean isAnswerable;
        public Integer latencyMs;
        public LocalDateTime createdAt;
    }

    public static class TimeseriesResponse {
        public int days;
        public List<TimeseriesPoint> points = new ArrayList<>();
    }

    public static class TimeseriesPoint {
        public LocalDate date;
        public long documentUploads;
        public long retrievalQueries;
        public long experimentsCreated;
    }

    public static class HealthResponse {
        public String status;
        public List<HealthItem> items = new ArrayList<>();
    }

    public static class HealthItem {
        public String key;
        public String label;
        public String status;
        public long count;
        public String message;
    }
}
