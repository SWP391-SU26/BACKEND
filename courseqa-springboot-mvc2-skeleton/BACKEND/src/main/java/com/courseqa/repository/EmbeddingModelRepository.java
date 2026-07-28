package com.courseqa.repository;

import com.courseqa.model.entity.EmbeddingModel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingModelRepository extends JpaRepository<EmbeddingModel, UUID> {
    boolean existsByModelName(String modelName);

    List<EmbeddingModel> findByIsActiveTrueOrderByCreatedAtDesc();

    long countByIsActiveTrue();
}
