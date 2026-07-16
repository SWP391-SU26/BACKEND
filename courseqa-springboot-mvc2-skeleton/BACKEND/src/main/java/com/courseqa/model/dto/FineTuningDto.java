package com.courseqa.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// DTOs for fine-tuning dataset export and experiment tracking.
// TODO: Add request/response DTO classes here.

public class FineTuningDto {
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class CreateFineTuningExperimentRequest {
        private String name;
        private UUID datasetId;
        private UUID researcherId;
        private String llmModel;
        private String configJson;
    }

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FineTuningExperimentResponse {
        private UUID experimentId;
        private String experimentName;
        private UUID researcherId;
        private String configJson;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileListResponse {
        private List<String> files;
    }
}
    
