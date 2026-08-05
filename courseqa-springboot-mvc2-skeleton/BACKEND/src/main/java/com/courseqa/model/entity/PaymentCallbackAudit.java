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
@Table(name = "payment_callback_audits")
public class PaymentCallbackAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "callback_audit_id")
    private UUID callbackAuditId;
    @Column(name = "payment_order_id")
    private UUID paymentOrderId;
    @Column(name = "vnp_txn_ref")
    private String vnpTxnRef;
    @Column(name = "callback_source")
    private String callbackSource;
    @Column(name = "checksum_valid")
    private Boolean checksumValid;
    @Column(name = "merchant_valid")
    private Boolean merchantValid;
    @Column(name = "amount_valid")
    private Boolean amountValid;
    @Column(name = "order_state_valid")
    private Boolean orderStateValid;
    @Column(name = "validation_error")
    private String validationError;
    @Column(name = "gateway_transaction_no")
    private String gatewayTransactionNo;
    @Column(name = "gateway_response_code")
    private String gatewayResponseCode;
    @Column(name = "gateway_transaction_status")
    private String gatewayTransactionStatus;
    @Column(name = "payload_json", columnDefinition = "NVARCHAR(MAX)")
    private String payloadJson;
    @Column(name = "merchant_rsp_code")
    private String merchantRspCode;
    @Column(name = "merchant_message")
    private String merchantMessage;
    @Column(name = "client_ip")
    private String clientIp;
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    public UUID getCallbackAuditId() { return callbackAuditId; }
    public void setCallbackAuditId(UUID value) { this.callbackAuditId = value; }
    public UUID getPaymentOrderId() { return paymentOrderId; }
    public void setPaymentOrderId(UUID value) { this.paymentOrderId = value; }
    public String getVnpTxnRef() { return vnpTxnRef; }
    public void setVnpTxnRef(String value) { this.vnpTxnRef = value; }
    public String getCallbackSource() { return callbackSource; }
    public void setCallbackSource(String value) { this.callbackSource = value; }
    public Boolean getChecksumValid() { return checksumValid; }
    public void setChecksumValid(Boolean value) { this.checksumValid = value; }
    public Boolean getMerchantValid() { return merchantValid; }
    public void setMerchantValid(Boolean value) { this.merchantValid = value; }
    public Boolean getAmountValid() { return amountValid; }
    public void setAmountValid(Boolean value) { this.amountValid = value; }
    public Boolean getOrderStateValid() { return orderStateValid; }
    public void setOrderStateValid(Boolean value) { this.orderStateValid = value; }
    public String getValidationError() { return validationError; }
    public void setValidationError(String value) { this.validationError = value; }
    public String getGatewayTransactionNo() { return gatewayTransactionNo; }
    public void setGatewayTransactionNo(String value) { this.gatewayTransactionNo = value; }
    public String getGatewayResponseCode() { return gatewayResponseCode; }
    public void setGatewayResponseCode(String value) { this.gatewayResponseCode = value; }
    public String getGatewayTransactionStatus() { return gatewayTransactionStatus; }
    public void setGatewayTransactionStatus(String value) { this.gatewayTransactionStatus = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { this.payloadJson = value; }
    public String getMerchantRspCode() { return merchantRspCode; }
    public void setMerchantRspCode(String value) { this.merchantRspCode = value; }
    public String getMerchantMessage() { return merchantMessage; }
    public void setMerchantMessage(String value) { this.merchantMessage = value; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String value) { this.clientIp = value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime value) { this.receivedAt = value; }
}
