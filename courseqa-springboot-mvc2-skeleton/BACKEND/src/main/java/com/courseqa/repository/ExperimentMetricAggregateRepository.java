package com.courseqa.repository;

import com.courseqa.model.entity.ExperimentMetricAggregate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentMetricAggregateRepository extends JpaRepository<ExperimentMetricAggregate, UUID> {
    List<ExperimentMetricAggregate> findByExperimentIdOrderByMetricNameAsc(UUID experimentId);
    void deleteByExperimentId(UUID experimentId);
}
