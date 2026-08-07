package com.courseqa.service;

import com.courseqa.config.VnpayProperties;
import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.PaymentCallbackAudit;
import com.courseqa.model.entity.PaymentOrder;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.model.entity.User;
import com.courseqa.repository.PaymentCallbackAuditRepository;
import com.courseqa.repository.PaymentOrderRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.service.VnpayGatewayService.CallbackVerification;
import com.courseqa.service.VnpaySettlementService.SettlementResult;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TXN_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final PaymentOrderRepository orders;
    private final PaymentCallbackAuditRepository audits;
    private final UserRepository users;
    private final PlanService planService;
    private final VnpayGatewayService gateway;
    private final VnpaySettlementService settlement;
    private final PaymentCallbackAuditService auditService;
    private final VnpayProperties properties;
    private final SubscriptionService subscriptionService;

    public PaymentService(
            PaymentOrderRepository orders,
            PaymentCallbackAuditRepository audits,
            UserRepository users,
            PlanService planService,
            VnpayGatewayService gateway,
            VnpaySettlementService settlement,
            PaymentCallbackAuditService auditService,
            VnpayProperties properties,
            SubscriptionService subscriptionService
    ) {
        this.orders = orders;
        this.audits = audits;
        this.users = users;
        this.planService = planService;
        this.gateway = gateway;
        this.settlement = settlement;
        this.auditService = auditService;
        this.properties = properties;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public PaymentDto.CreateOrderResponse createOrder(UUID userId, String requestedPlanCode, String clientIp) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPay is not configured yet. Please contact the administrator.");
        }
        properties.assertReady();
        String planCode = requestedPlanCode == null ? "PRO" : requestedPlanCode.trim().toUpperCase(Locale.ROOT);
        User user = users.findById(userId)
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Active user not found."));
        SubscriptionPlan plan = planService.requireActive(planCode);
        if (plan.getPriceVnd() == null || plan.getPriceVnd() <= 0 || plan.getDurationDays() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active paid plans can be purchased.");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        subscriptionService.assertPurchaseAllowed(userId);
        PaymentOrder pending = orders
                .findFirstByUserIdAndPlanCodeSnapshotAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId, plan.getPlanCode(), "PENDING", now)
                .orElse(null);
        if (pending != null) {
            return toCreateOrderResponse(pending);
        }
        PaymentOrder order = new PaymentOrder();
        order.setVnpTxnRef(newTxnRef(planCode, now));
        order.setUserId(user.getUserId());
        order.setPlanId(plan.getPlanId());
        order.setPlanCodeSnapshot(plan.getPlanCode());
        order.setAmountVnd(plan.getPriceVnd());
        order.setDurationDays(plan.getDurationDays());
        order.setGateway("VNPAY");
        order.setStatus("PENDING");
        order.setClientIp(normalizedIp(clientIp));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setExpiresAt(now.plusMinutes(properties.getOrderTtlMinutes()));
        order = orders.save(order);

        return toCreateOrderResponse(order);
    }

    private PaymentDto.CreateOrderResponse toCreateOrderResponse(PaymentOrder order) {
        PaymentDto.CreateOrderResponse response = new PaymentDto.CreateOrderResponse();
        response.orderId = order.getPaymentOrderId();
        response.txnRef = order.getVnpTxnRef();
        response.paymentUrl = gateway.createPaymentUrl(order);
        response.status = order.getStatus();
        response.amountVnd = order.getAmountVnd();
        response.expiresAt = order.getExpiresAt();
        return response;
    }

    @Transactional
    public PaymentDto.OrderResponse getOwnedOrder(UUID orderId, UUID userId) {
        PaymentOrder order = orders.findByPaymentOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found."));
        expirePending(order);
        return toOrderResponse(order);
    }

    @Transactional
    public PaymentDto.UserPaymentPageResponse userPayments(UUID userId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        Set<String> supported = Set.of("PENDING", "PAID", "FAILED", "EXPIRED", "CANCELLED");
        if (!normalizedStatus.isBlank() && !supported.contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment status.");
        }
        Specification<PaymentOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (!normalizedStatus.isBlank()) predicates.add(cb.equal(root.get("status"), normalizedStatus));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<PaymentOrder> result = orders.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        result.getContent().forEach(this::expirePending);
        PaymentDto.UserPaymentPageResponse response = new PaymentDto.UserPaymentPageResponse();
        response.items = result.getContent().stream().map(this::toOrderResponse).toList();
        response.page = result.getNumber();
        response.size = result.getSize();
        response.totalElements = result.getTotalElements();
        response.totalPages = result.getTotalPages();
        return response;
    }

    public PaymentDto.IpnResponse handleIpn(Map<String, String> rawParams, String clientIp) {
        if (!properties.isEnabled()) {
            Map<String, String> params = gateway.sanitizedParams(rawParams);
            auditService.record("IPN", params, findByTxnRef(params.get("vnp_TxnRef")), false,
                    null, null, null, "PAYMENT_DISABLED", "99", "Payment disabled", clientIp);
            return new PaymentDto.IpnResponse("99", "Payment disabled");
        }
        CallbackVerification verification = gateway.verify(rawParams);
        Map<String, String> params = verification.params();
        if (!verification.valid()) {
            auditService.record("IPN", params, null, false, null, null, null,
                    "INVALID_SIGNATURE", "97", "Invalid signature", clientIp);
            return new PaymentDto.IpnResponse("97", "Invalid signature");
        }
        boolean merchantValid = properties.getTmnCode().equals(params.get("vnp_TmnCode"));
        if (!merchantValid) {
            PaymentOrder order = findByTxnRef(params.get("vnp_TxnRef"));
            auditService.record("IPN", params, order, true, false, null, null,
                    "MERCHANT_MISMATCH", "99", "Invalid merchant", clientIp);
            return new PaymentDto.IpnResponse("99", "Invalid merchant");
        }
        try {
            SettlementResult result = settlement.settle(params);
            auditService.record("IPN", params, result.order(), true, true, result.amountValid(),
                    result.orderStateValid(), result.validationError(), result.rspCode(), result.message(), clientIp);
            return new PaymentDto.IpnResponse(result.rspCode(), result.message());
        } catch (Exception exception) {
            PaymentOrder order = findByTxnRef(params.get("vnp_TxnRef"));
            auditService.record("IPN", params, order, true, true, null, null,
                    "INTERNAL_ERROR", "99", "Unknown error", clientIp);
            return new PaymentDto.IpnResponse("99", "Unknown error");
        }
    }

    public ReturnResult inspectReturn(Map<String, String> rawParams, String clientIp) {
        if (!properties.isEnabled()) {
            Map<String, String> params = gateway.sanitizedParams(rawParams);
            PaymentOrder order = findByTxnRef(params.get("vnp_TxnRef"));
            auditService.record("RETURN", params, order, false, null, null, null,
                    "PAYMENT_DISABLED", null, null, clientIp);
            return new ReturnResult(null, false, "PAYMENT_DISABLED");
        }
        CallbackVerification verification = gateway.verify(rawParams);
        Map<String, String> params = verification.params();
        PaymentOrder order = findByTxnRef(params.get("vnp_TxnRef"));
        boolean merchantValid = verification.valid()
                && properties.getTmnCode().equals(params.get("vnp_TmnCode"));
        Boolean amountValid = order == null ? null : matchesAmount(params.get("vnp_Amount"), order.getAmountVnd());
        boolean valid = verification.valid() && merchantValid && order != null && Boolean.TRUE.equals(amountValid);
        String error = !verification.valid() ? "INVALID_SIGNATURE"
                : !merchantValid ? "MERCHANT_MISMATCH"
                : order == null ? "ORDER_NOT_FOUND"
                : !Boolean.TRUE.equals(amountValid) ? "AMOUNT_MISMATCH" : null;
        if (!valid) {
            auditService.record("RETURN", params, order, verification.valid(), merchantValid, amountValid,
                    order == null ? null : "PENDING".equals(order.getStatus()), error, null, null, clientIp);
            return new ReturnResult(null, false, error);
        }
        try {
            SettlementResult result = settlement.settle(params);
            PaymentOrder settledOrder = result.order() == null ? order : result.order();
            auditService.record("RETURN", params, settledOrder, true, true, result.amountValid(),
                    result.orderStateValid(), result.validationError(), result.rspCode(), result.message(), clientIp);
            return new ReturnResult(settledOrder.getPaymentOrderId(), true, result.validationError());
        } catch (Exception exception) {
            auditService.record("RETURN", params, order, true, true, amountValid,
                    null, "INTERNAL_ERROR", "99", "Unknown error", clientIp);
            return new ReturnResult(order.getPaymentOrderId(), true, "INTERNAL_ERROR");
        }
    }

    public PaymentDto.AdminPaymentPageResponse adminPayments(
            String status, String search, LocalDate from, LocalDate to, int page, int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Set<UUID> matchingUsers = matchingUserIds(search);
        Specification<PaymentOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate txnMatch = cb.like(cb.lower(root.get("vnpTxnRef")), pattern);
                Predicate transactionMatch = cb.like(cb.lower(root.get("gatewayTransactionNo")), pattern);
                Predicate userMatch = matchingUsers.isEmpty()
                        ? cb.disjunction() : root.get("userId").in(matchingUsers);
                predicates.add(cb.or(txnMatch, transactionMatch, userMatch));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to.atTime(LocalTime.MAX)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<PaymentOrder> result = orders.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        PaymentDto.AdminPaymentPageResponse response = new PaymentDto.AdminPaymentPageResponse();
        response.items = result.getContent().stream().map(this::toOrderResponse).toList();
        response.page = result.getNumber();
        response.size = result.getSize();
        response.totalElements = result.getTotalElements();
        response.totalPages = result.getTotalPages();
        return response;
    }

    public PaymentDto.AdminPaymentDetailResponse adminPayment(UUID orderId) {
        PaymentOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found."));
        PaymentDto.AdminPaymentDetailResponse response = new PaymentDto.AdminPaymentDetailResponse();
        response.order = toOrderResponse(order);
        response.callbacks = audits.findByPaymentOrderIdOrderByReceivedAtDesc(orderId).stream()
                .map(this::toAuditResponse).toList();
        return response;
    }

    private PaymentDto.OrderResponse toOrderResponse(PaymentOrder order) {
        PaymentDto.OrderResponse response = new PaymentDto.OrderResponse();
        response.orderId = order.getPaymentOrderId();
        response.txnRef = order.getVnpTxnRef();
        response.userId = order.getUserId();
        users.findById(order.getUserId()).ifPresent(user -> {
            response.userEmail = user.getEmail();
            response.userFullName = user.getFullName();
        });
        response.planCode = order.getPlanCodeSnapshot();
        response.amountVnd = order.getAmountVnd();
        response.durationDays = order.getDurationDays();
        response.gateway = order.getGateway();
        response.status = order.getStatus();
        response.gatewayTransactionNo = order.getGatewayTransactionNo();
        response.gatewayResponseCode = order.getGatewayResponseCode();
        response.gatewayTransactionStatus = order.getGatewayTransactionStatus();
        response.bankCode = order.getBankCode();
        response.gatewayPayDate = order.getGatewayPayDate();
        response.paidAt = order.getPaidAt();
        response.activatedAt = order.getActivatedAt();
        response.expiresAt = order.getExpiresAt();
        response.createdAt = order.getCreatedAt();
        response.updatedAt = order.getUpdatedAt();
        return response;
    }

    private PaymentDto.CallbackAuditResponse toAuditResponse(PaymentCallbackAudit item) {
        PaymentDto.CallbackAuditResponse response = new PaymentDto.CallbackAuditResponse();
        response.callbackId = item.getCallbackAuditId();
        response.txnRef = item.getVnpTxnRef();
        response.source = item.getCallbackSource();
        response.checksumValid = Boolean.TRUE.equals(item.getChecksumValid());
        response.merchantValid = item.getMerchantValid();
        response.amountValid = item.getAmountValid();
        response.orderStateValid = item.getOrderStateValid();
        response.validationError = item.getValidationError();
        response.gatewayTransactionNo = item.getGatewayTransactionNo();
        response.gatewayResponseCode = item.getGatewayResponseCode();
        response.gatewayTransactionStatus = item.getGatewayTransactionStatus();
        response.payloadJson = item.getPayloadJson();
        response.merchantRspCode = item.getMerchantRspCode();
        response.merchantMessage = item.getMerchantMessage();
        response.clientIp = item.getClientIp();
        response.receivedAt = item.getReceivedAt();
        return response;
    }

    private void expirePending(PaymentOrder order) {
        if ("PENDING".equals(order.getStatus())
                && LocalDateTime.now(BUSINESS_ZONE).isAfter(order.getExpiresAt())) {
            order.setStatus("EXPIRED");
            order.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
            orders.save(order);
        }
    }

    private Set<UUID> matchingUserIds(String search) {
        if (search == null || search.isBlank()) return Set.of();
        return users.findTop50ByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(search.trim(), search.trim())
                .stream().map(User::getUserId).collect(java.util.stream.Collectors.toSet());
    }

    private PaymentOrder findByTxnRef(String txnRef) {
        return txnRef == null ? null : orders.findByVnpTxnRef(txnRef).orElse(null);
    }

    private static Boolean matchesAmount(String rawAmount, Long amountVnd) {
        try {
            if (rawAmount == null || amountVnd == null) return false;
            return Math.multiplyExact(amountVnd, 100L) == Long.parseLong(rawAmount);
        } catch (ArithmeticException | NumberFormatException ignored) {
            return false;
        }
    }

    private static String newTxnRef(String planCode, LocalDateTime now) {
        String prefix = planCode == null ? "PLAN" : planCode.replaceAll("[^A-Z0-9]", "");
        if (prefix.isBlank()) prefix = "PLAN";
        return prefix + "-" + TXN_DATE.format(now) + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalizedIp(String ip) {
        if (ip == null || ip.isBlank()) return "127.0.0.1";
        String first = ip.split(",", 2)[0].trim();
        return first.length() <= 45 ? first : first.substring(0, 45);
    }

    public record ReturnResult(UUID orderId, boolean valid, String error) { }
}
