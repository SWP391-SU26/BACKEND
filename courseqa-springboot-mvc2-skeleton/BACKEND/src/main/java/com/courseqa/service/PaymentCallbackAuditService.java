package com.courseqa.service;

import com.courseqa.model.entity.PaymentCallbackAudit;
import com.courseqa.model.entity.PaymentOrder;
import com.courseqa.repository.PaymentCallbackAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCallbackAuditService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PaymentCallbackAuditRepository audits;
    private final ObjectMapper objectMapper;

    public PaymentCallbackAuditService(PaymentCallbackAuditRepository audits, ObjectMapper objectMapper) {
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String source,
            Map<String, String> sanitizedParams,
            PaymentOrder order,
            boolean checksumValid,
            Boolean merchantValid,
            Boolean amountValid,
            Boolean orderStateValid,
            String validationError,
            String rspCode,
            String message,
            String clientIp
    ) {
        PaymentCallbackAudit audit = new PaymentCallbackAudit();
        audit.setPaymentOrderId(order == null ? null : order.getPaymentOrderId());
        audit.setVnpTxnRef(sanitizedParams.get("vnp_TxnRef"));
        audit.setCallbackSource(source);
        audit.setChecksumValid(checksumValid);
        audit.setMerchantValid(merchantValid);
        audit.setAmountValid(amountValid);
        audit.setOrderStateValid(orderStateValid);
        audit.setValidationError(validationError);
        audit.setGatewayTransactionNo(sanitizedParams.get("vnp_TransactionNo"));
        audit.setGatewayResponseCode(sanitizedParams.get("vnp_ResponseCode"));
        audit.setGatewayTransactionStatus(sanitizedParams.get("vnp_TransactionStatus"));
        audit.setPayloadJson(toJson(sanitizedParams));
        audit.setMerchantRspCode(rspCode);
        audit.setMerchantMessage(message);
        audit.setClientIp(normalizedIp(clientIp));
        audit.setReceivedAt(LocalDateTime.now(BUSINESS_ZONE));
        audits.save(audit);
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String normalizedIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        String first = ip.split(",", 2)[0].trim();
        return first.length() <= 45 ? first : first.substring(0, 45);
    }
}
