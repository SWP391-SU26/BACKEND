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
@Table(name = "subscription_history")
public class SubscriptionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "subscription_history_id")
    private UUID subscriptionHistoryId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "plan_id")
    private UUID planId;
    @Column(name = "payment_order_id")
    private UUID paymentOrderId;
    @Column(name = "extension_from")
    private LocalDateTime extensionFrom;
    @Column(name = "extension_to")
    private LocalDateTime extensionTo;
    @Column(name = "days_added")
    private Integer daysAdded;
    @Column(name = "amount_vnd")
    private Long amountVnd;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UUID getSubscriptionHistoryId() { return subscriptionHistoryId; }
    public void setSubscriptionHistoryId(UUID value) { this.subscriptionHistoryId = value; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID value) { this.userId = value; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID value) { this.planId = value; }
    public UUID getPaymentOrderId() { return paymentOrderId; }
    public void setPaymentOrderId(UUID value) { this.paymentOrderId = value; }
    public LocalDateTime getExtensionFrom() { return extensionFrom; }
    public void setExtensionFrom(LocalDateTime value) { this.extensionFrom = value; }
    public LocalDateTime getExtensionTo() { return extensionTo; }
    public void setExtensionTo(LocalDateTime value) { this.extensionTo = value; }
    public Integer getDaysAdded() { return daysAdded; }
    public void setDaysAdded(Integer value) { this.daysAdded = value; }
    public Long getAmountVnd() { return amountVnd; }
    public void setAmountVnd(Long value) { this.amountVnd = value; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime value) { this.paidAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}
