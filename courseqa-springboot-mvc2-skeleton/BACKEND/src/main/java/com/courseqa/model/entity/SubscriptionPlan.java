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
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_code")
    private String planCode;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "price_vnd")
    private Long priceVnd;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "max_file_bytes")
    private Long maxFileBytes;

    @Column(name = "max_documents")
    private Integer maxDocuments;

    @Column(name = "max_storage_bytes")
    private Long maxStorageBytes;

    @Column(name = "max_personal_workspaces")
    private Integer maxPersonalWorkspaces;

    @Column(name = "benefits_json", columnDefinition = "NVARCHAR(MAX)")
    private String benefitsJson;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Long getPriceVnd() { return priceVnd; }
    public void setPriceVnd(Long priceVnd) { this.priceVnd = priceVnd; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Long getMaxFileBytes() { return maxFileBytes; }
    public void setMaxFileBytes(Long maxFileBytes) { this.maxFileBytes = maxFileBytes; }
    public Integer getMaxDocuments() { return maxDocuments; }
    public void setMaxDocuments(Integer maxDocuments) { this.maxDocuments = maxDocuments; }
    public Long getMaxStorageBytes() { return maxStorageBytes; }
    public void setMaxStorageBytes(Long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
    public Integer getMaxPersonalWorkspaces() { return maxPersonalWorkspaces; }
    public void setMaxPersonalWorkspaces(Integer maxPersonalWorkspaces) { this.maxPersonalWorkspaces = maxPersonalWorkspaces; }
    public String getBenefitsJson() { return benefitsJson; }
    public void setBenefitsJson(String benefitsJson) { this.benefitsJson = benefitsJson; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
