package com.courseqa.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PaymentDto {
    private PaymentDto() { }

    public static class PlanResponse {
        public UUID planId;
        public String planCode;
        public String displayName;
        public long priceVnd;
        public Integer durationDays;
        public long maxFileBytes;
        public int maxDocuments;
        public long maxStorageBytes;
        public int maxPersonalWorkspaces;
        public List<String> benefits = new ArrayList<>();
        public boolean isActive;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    public static class PlanUpsertRequest {
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{2,20}")
        public String planCode;

        @NotBlank
        @Size(max = 100)
        public String displayName;

        @NotNull
        @PositiveOrZero
        public Long priceVnd;

        @Positive
        public Integer durationDays;

        @NotNull
        @Positive
        public Long maxFileBytes;

        @NotNull
        @Positive
        public Integer maxDocuments;

        @NotNull
        @Positive
        public Long maxStorageBytes;

        @NotNull
        @Positive
        public Integer maxPersonalWorkspaces;

        @NotNull
        @Size(max = 20)
        public List<@NotBlank @Size(max = 240) String> benefits = new ArrayList<>();

        public Boolean isActive = true;
    }

    public static class PlanDeleteResponse {
        public boolean deleted;
        public boolean deactivated;
        public String message;

        public PlanDeleteResponse(boolean deleted, boolean deactivated, String message) {
            this.deleted = deleted;
            this.deactivated = deactivated;
            this.message = message;
        }
    }

    public static class CreateOrderRequest {
        public String planCode = "PRO";
    }

    public static class CreateOrderResponse {
        public UUID orderId;
        public String txnRef;
        public String paymentUrl;
        public String status;
        public long amountVnd;
        public LocalDateTime expiresAt;
    }

    public static class OrderResponse {
        public UUID orderId;
        public String txnRef;
        public UUID userId;
        public String userEmail;
        public String userFullName;
        public String planCode;
        public long amountVnd;
        public int durationDays;
        public String gateway;
        public String status;
        public String gatewayTransactionNo;
        public String gatewayResponseCode;
        public String gatewayTransactionStatus;
        public String bankCode;
        public LocalDateTime gatewayPayDate;
        public LocalDateTime paidAt;
        public LocalDateTime activatedAt;
        public LocalDateTime expiresAt;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    public static class SubscriptionResponse {
        public String status;
        public String effectivePlanCode;
        public LocalDateTime startedAt;
        public LocalDateTime expiresAt;
        public PlanResponse plan;
    }

    /** REQ-02 WS-US-02: lets the upload UI show remaining quota before it fails a request. */
    public static class StorageUsageResponse {
        public long usedBytes;
        public long maxStorageBytes;
        public int documentCount;
        public int maxDocuments;
        public long maxFileBytes;
        public int workspaceCount;
        public int maxPersonalWorkspaces;
    }

    public static class SubscriptionHistoryResponse {
        public UUID historyId;
        public UUID orderId;
        public String planCode;
        public LocalDateTime extensionFrom;
        public LocalDateTime extensionTo;
        public int daysAdded;
        public long amountVnd;
        public LocalDateTime paidAt;
    }

    public static class CallbackAuditResponse {
        public UUID callbackId;
        public String txnRef;
        public String source;
        public boolean checksumValid;
        public Boolean merchantValid;
        public Boolean amountValid;
        public Boolean orderStateValid;
        public String validationError;
        public String gatewayTransactionNo;
        public String gatewayResponseCode;
        public String gatewayTransactionStatus;
        public String payloadJson;
        public String merchantRspCode;
        public String merchantMessage;
        public String clientIp;
        public LocalDateTime receivedAt;
    }

    public static class AdminPaymentDetailResponse {
        public OrderResponse order;
        public List<CallbackAuditResponse> callbacks = new ArrayList<>();
    }

    public static class AdminPaymentPageResponse {
        public List<OrderResponse> items = new ArrayList<>();
        public int page;
        public int size;
        public long totalElements;
        public int totalPages;
    }

    public static class UserPaymentPageResponse {
        public List<OrderResponse> items = new ArrayList<>();
        public int page;
        public int size;
        public long totalElements;
        public int totalPages;
    }

    public static class IpnResponse {
        @JsonProperty("RspCode")
        public String rspCode;

        @JsonProperty("Message")
        public String message;

        public IpnResponse(String rspCode, String message) {
            this.rspCode = rspCode;
            this.message = message;
        }
    }
}
