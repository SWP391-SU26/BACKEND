package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "embedding_models")
public class EmbeddingModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "embedding_model_id")
    private UUID embeddingModelId;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "provider")
    private String provider;

    @Column(name = "dimension")
    private Integer dimension;

    @Column(name = "is_local")
    private Boolean isLocal;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "config_json", columnDefinition = "NVARCHAR(MAX)")
    private String configJson;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public EmbeddingModel() { }

    public UUID getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(UUID embeddingModelId) { this.embeddingModelId = embeddingModelId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }

    public Boolean getIsLocal() { return isLocal; }
    public void setIsLocal(Boolean isLocal) { this.isLocal = isLocal; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }


}
