package com.courseqa.model.dto;

import java.util.UUID;

// DTOs for fine-tuning dataset export and experiment tracking.
// TODO: Add request/response DTO classes here.

public class FineTuningDto {
public static class ExportResponse {
        public UUID datasetId;
        public String filePath;
        public Integer totalLines;

        public ExportResponse(UUID datasetId, String filePath, Integer totalLines) {
            this.datasetId = datasetId;
            this.filePath = filePath;
            this.totalLines = totalLines;
        }
    }
}
    