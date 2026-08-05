package com.courseqa.service;

import com.courseqa.model.entity.PaymentOrder;
import com.courseqa.model.entity.SubscriptionHistory;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.model.entity.UserSubscription;
import com.courseqa.repository.PaymentOrderRepository;
import com.courseqa.repository.SubscriptionHistoryRepository;
import com.courseqa.repository.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnpaySettlementService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final PaymentOrderRepository orders;
    private final UserSubscriptionRepository subscriptions;
    private final SubscriptionHistoryRepository history;
    private final PlanService planService;

    public VnpaySettlementService(
            PaymentOrderRepository orders,
            UserSubscriptionRepository subscriptions,
            SubscriptionHistoryRepository history,
            PlanService planService
    ) {
        this.orders = orders;
        this.subscriptions = subscriptions;
        this.history = history;
        this.planService = planService;
    }

    @Transactional
    public SettlementResult settle(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        PaymentOrder order = orders.findForUpdateByVnpTxnRef(txnRef).orElse(null);
        if (order == null || !"VNPAY".equals(order.getGateway())) {
            return SettlementResult.failure(null, true, null, null, "ORDER_NOT_FOUND", "01", "Order not found");
        }

        Long amountVnd = parseAmount(params.get("vnp_Amount"));
        boolean amountValid = amountVnd != null && amountVnd.equals(order.getAmountVnd());
        if (!amountValid) {
            return SettlementResult.failure(order, false, false, null, "AMOUNT_MISMATCH", "04", "Invalid amount");
        }
        if (!"PENDING".equals(order.getStatus())) {
            return SettlementResult.failure(order, true, true, false, "ORDER_ALREADY_CONFIRMED", "02", "Order already confirmed");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime gatewayPayDate;
        try {
            gatewayPayDate = parsePayDate(params.get("vnp_PayDate"));
        } catch (DateTimeParseException exception) {
            return SettlementResult.failure(order, true, true, true, "INVALID_PAY_DATE", "99", "Invalid payment data");
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);
        LocalDateTime paidAt = gatewayPayDate != null ? gatewayPayDate : now;

        copyGatewayFields(order, params, gatewayPayDate, now);
        if (success) {
            if (blank(params.get("vnp_TransactionNo"))) {
                return SettlementResult.failure(order, true, true, true,
                        "MISSING_TRANSACTION_NO", "99", "Invalid payment data");
            }
            boolean paidAfterExpiry = paidAt.isAfter(order.getExpiresAt());
            boolean expiredWithoutTrustedPayDate = gatewayPayDate == null && now.isAfter(order.getExpiresAt());
            if (paidAfterExpiry || expiredWithoutTrustedPayDate) {
                order.setStatus("EXPIRED");
                orders.save(order);
                return SettlementResult.failure(order, true, true, true,
                        "ORDER_EXPIRED", "00", "Confirm Success");
            }
            if (!activate(order, paidAt, now)) {
                return SettlementResult.failure(order, true, true, false,
                        "ACTIVE_SUBSCRIPTION_EXISTS", "00", "Confirm Success");
            }
            return SettlementResult.success(order);
        }

        order.setStatus(statusFor(responseCode, now, order.getExpiresAt()));
        orders.save(order);
        return SettlementResult.failure(order, true, true, true,
                "VNPAY_" + safe(responseCode), "00", "Confirm Success");
    }

    private boolean activate(PaymentOrder order, LocalDateTime paidAt, LocalDateTime now) {
        SubscriptionPlan plan = planService.requireById(order.getPlanId());
        UserSubscription subscription = subscriptions.findForUpdateByUserId(order.getUserId())
                .orElseGet(() -> newSubscription(order.getUserId()));

        boolean currentlyActive = "PRO_ACTIVE".equals(subscription.getStatus())
                && subscription.getExpiresAt() != null
                && subscription.getExpiresAt().isAfter(now);
        if (currentlyActive) {
            order.setStatus("FAILED");
            order.setUpdatedAt(now);
            orders.save(order);
            return false;
        }
        LocalDateTime extensionFrom = paidAt;
        LocalDateTime extensionTo = paidAt.plusDays(order.getDurationDays());

        subscription.setPlanId(plan.getPlanId());
        subscription.setStatus("PRO_ACTIVE");
        subscription.setStartedAt(paidAt);
        subscription.setExpiresAt(extensionTo);
        subscription.setUpdatedAt(now);
        subscriptions.save(subscription);

        SubscriptionHistory event = new SubscriptionHistory();
        event.setUserId(order.getUserId());
        event.setPlanId(plan.getPlanId());
        event.setPaymentOrderId(order.getPaymentOrderId());
        event.setExtensionFrom(extensionFrom);
        event.setExtensionTo(extensionTo);
        event.setDaysAdded(order.getDurationDays());
        event.setAmountVnd(order.getAmountVnd());
        event.setPaidAt(paidAt);
        event.setCreatedAt(now);
        history.save(event);

        order.setStatus("PAID");
        order.setPaidAt(paidAt);
        order.setActivatedAt(now);
        order.setUpdatedAt(now);
        orders.save(order);
        return true;
    }

    private UserSubscription newSubscription(java.util.UUID userId) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planService.requireActive("FREE").getPlanId());
        subscription.setStatus("FREE");
        subscription.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        return subscription;
    }

    private static void copyGatewayFields(
            PaymentOrder order, Map<String, String> params, LocalDateTime gatewayPayDate, LocalDateTime now
    ) {
        order.setGatewayTransactionNo(blank(params.get("vnp_TransactionNo")) ? null : params.get("vnp_TransactionNo"));
        order.setGatewayResponseCode(params.get("vnp_ResponseCode"));
        order.setGatewayTransactionStatus(params.get("vnp_TransactionStatus"));
        order.setBankCode(params.get("vnp_BankCode"));
        order.setGatewayPayDate(gatewayPayDate);
        order.setUpdatedAt(now);
    }

    private static Long parseAmount(String raw) {
        try {
            if (raw == null) return null;
            long scaled = Long.parseLong(raw);
            return scaled >= 0 && scaled % 100 == 0 ? scaled / 100 : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static LocalDateTime parsePayDate(String raw) {
        return blank(raw) ? null : LocalDateTime.parse(raw, VNPAY_DATE);
    }

    private static String statusFor(String responseCode, LocalDateTime now, LocalDateTime expiresAt) {
        if ("24".equals(responseCode)) return "CANCELLED";
        if ("11".equals(responseCode) || now.isAfter(expiresAt)) return "EXPIRED";
        return "FAILED";
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safe(String value) { return value == null || value.isBlank() ? "UNKNOWN" : value; }

    public record SettlementResult(
            PaymentOrder order,
            boolean checksumValid,
            Boolean amountValid,
            Boolean orderStateValid,
            String validationError,
            String rspCode,
            String message
    ) {
        static SettlementResult success(PaymentOrder order) {
            return new SettlementResult(order, true, true, true, null, "00", "Confirm Success");
        }

        static SettlementResult failure(
                PaymentOrder order, boolean checksumValid, Boolean amountValid, Boolean orderStateValid,
                String validationError, String rspCode, String message
        ) {
            return new SettlementResult(order, checksumValid, amountValid, orderStateValid,
                    validationError, rspCode, message);
        }
    }
}
