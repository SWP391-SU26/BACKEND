package com.courseqa.repository;

import com.courseqa.model.entity.ExperimentResult;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExperimentResultRepository extends JpaRepository<ExperimentResult, UUID> {
    List<ExperimentResult> findByExperimentId(UUID experimentId);

    @Transactional
    void deleteByExperimentId(UUID experimentId);
}
