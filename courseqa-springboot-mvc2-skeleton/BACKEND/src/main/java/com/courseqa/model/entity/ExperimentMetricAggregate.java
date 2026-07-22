package com.courseqa.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "experiment_metric_aggregates")
public class ExperimentMetricAggregate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "aggregate_id")
    private UUID aggregateId;
    @Column(name = "experiment_id")
    private UUID experimentId;
    @Column(name = "metric_name")
    private String metricName;
    @Column(name = "average_value")
    private Double averageValue;
    @Column(name = "min_value")
    private Double minValue;
    @Column(name = "max_value")
    private Double maxValue;
    @Column(name = "standard_deviation")
    private Double standardDeviation;
    @Column(name = "sample_count")
    private Integer sampleCount;
    @Column(name = "failure_count")
    private Integer failureCount;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UUID getAggregateId() { return aggregateId; }
    public UUID getExperimentId() { return experimentId; }
    public void setExperimentId(UUID experimentId) { this.experimentId = experimentId; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public Double getAverageValue() { return averageValue; }
    public void setAverageValue(Double averageValue) { this.averageValue = averageValue; }
    public Double getMinValue() { return minValue; }
    public void setMinValue(Double minValue) { this.minValue = minValue; }
    public Double getMaxValue() { return maxValue; }
    public void setMaxValue(Double maxValue) { this.maxValue = maxValue; }
    public Double getStandardDeviation() { return standardDeviation; }
    public void setStandardDeviation(Double standardDeviation) { this.standardDeviation = standardDeviation; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
