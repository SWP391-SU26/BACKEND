package com.courseqa.model.dto;

import java.util.UUID;

public class ExperimentDto {
    
    public static class CreateExperimentRequest {
        public UUID datasetId;
        public UUID courseId;
        public UUID workspaceId;
        public String experimentName;
        public String experimentType = "RAG";
        public String llmModel = "simple-local-answerer";
        public UUID embeddingModelId;
        public String chunkingStrategy = "fixed_1200_150";
        public Integer topK = 5;
        public Double temperature = 0.2;
        public String fineTunedModelName;
        public String configJson;
        public UUID createdBy;
    }
}
