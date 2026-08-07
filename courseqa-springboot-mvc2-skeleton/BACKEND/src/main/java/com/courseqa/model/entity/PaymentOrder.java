package com.courseqa.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_order_id")
    private UUID paymentOrderId;
    @Column(name = "vnp_txn_ref")
    private String vnpTxnRef;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "plan_id")
    private UUID planId;
    @Column(name = "plan_code_snapshot")
    private String planCodeSnapshot;
    @Column(name = "amount_vnd")
    private Long amountVnd;
    @Column(name = "duration_days")
    private Integer durationDays;
    @Column(name = "gateway")
    private String gateway;
    @Column(name = "status")
    private String status;
    @Column(name = "client_ip")
    private String clientIp;
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    @Column(name = "gateway_transaction_no")
    private String gatewayTransactionNo;
    @Column(name = "gateway_response_code")
    private String gatewayResponseCode;
    @Column(name = "gateway_transaction_status")
    private String gatewayTransactionStatus;
    @Column(name = "bank_code")
    private String bankCode;
    @Column(name = "gateway_pay_date")
    private LocalDateTime gatewayPayDate;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    public UUID getPaymentOrderId() { return paymentOrderId; }
    public void setPaymentOrderId(UUID paymentOrderId) { this.paymentOrderId = paymentOrderId; }
    public String getVnpTxnRef() { return vnpTxnRef; }
    public void setVnpTxnRef(String vnpTxnRef) { this.vnpTxnRef = vnpTxnRef; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getPlanCodeSnapshot() { return planCodeSnapshot; }
    public void setPlanCodeSnapshot(String planCodeSnapshot) { this.planCodeSnapshot = planCodeSnapshot; }
    public Long getAmountVnd() { return amountVnd; }
    public void setAmountVnd(Long amountVnd) { this.amountVnd = amountVnd; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getGatewayTransactionNo() { return gatewayTransactionNo; }
    public void setGatewayTransactionNo(String gatewayTransactionNo) { this.gatewayTransactionNo = gatewayTransactionNo; }
    public String getGatewayResponseCode() { return gatewayResponseCode; }
    public void setGatewayResponseCode(String gatewayResponseCode) { this.gatewayResponseCode = gatewayResponseCode; }
    public String getGatewayTransactionStatus() { return gatewayTransactionStatus; }
    public void setGatewayTransactionStatus(String gatewayTransactionStatus) { this.gatewayTransactionStatus = gatewayTransactionStatus; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public LocalDateTime getGatewayPayDate() { return gatewayPayDate; }
    public void setGatewayPayDate(LocalDateTime gatewayPayDate) { this.gatewayPayDate = gatewayPayDate; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
}
