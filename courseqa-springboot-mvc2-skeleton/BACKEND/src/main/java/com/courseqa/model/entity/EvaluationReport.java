package com.courseqa.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_reports")
public class EvaluationReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "dataset_id")
    private UUID datasetId;

    @Column(name = "rag_experiment_id")
    private UUID ragExperimentId;

    @Column(name = "fine_tuned_experiment_id")
    private UUID fineTunedExperimentId;

    @Column(name = "language")
    private String language;

    @Column(name = "title")
    private String title;

    @Column(name = "status")
    private String status;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "snapshot_json", columnDefinition = "NVARCHAR(MAX)")
    private String snapshotJson;

    @Column(name = "snapshot_checksum")
    private String snapshotChecksum;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "docx_path")
    private String docxPath;

    @Column(name = "csv_path")
    private String csvPath;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public UUID getRagExperimentId() { return ragExperimentId; }
    public void setRagExperimentId(UUID ragExperimentId) { this.ragExperimentId = ragExperimentId; }

    public UUID getFineTunedExperimentId() { return fineTunedExperimentId; }
    public void setFineTunedExperimentId(UUID fineTunedExperimentId) { this.fineTunedExperimentId = fineTunedExperimentId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }

    public String getSnapshotChecksum() { return snapshotChecksum; }
    public void setSnapshotChecksum(String snapshotChecksum) { this.snapshotChecksum = snapshotChecksum; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public String getDocxPath() { return docxPath; }
    public void setDocxPath(String docxPath) { this.docxPath = docxPath; }

    public String getCsvPath() { return csvPath; }
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
