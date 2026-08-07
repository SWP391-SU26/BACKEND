package com.courseqa.service;

import com.courseqa.config.VnpayProperties;
import com.courseqa.model.entity.PaymentOrder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class VnpayGatewayService {
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayProperties properties;

    public VnpayGatewayService(VnpayProperties properties) {
        this.properties = properties;
    }

    public String createPaymentUrl(PaymentOrder order) {
        properties.assertReady();
        TreeMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(Math.multiplyExact(order.getAmountVnd(), 100L)));
        params.put("vnp_CreateDate", VNPAY_DATE.format(order.getCreatedAt()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", normalizedIp(order.getClientIp()));
        params.put("vnp_Locale", "vn");
        String planCode = order.getPlanCodeSnapshot() == null || order.getPlanCodeSnapshot().isBlank()
                ? "dich vu" : order.getPlanCodeSnapshot().trim();
        params.put("vnp_OrderInfo", "Thanh toan goi " + planCode + " " + order.getVnpTxnRef());
        params.put("vnp_OrderType", "other");
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_ExpireDate", VNPAY_DATE.format(order.getExpiresAt()));
        params.put("vnp_TxnRef", order.getVnpTxnRef());

        String query = canonicalQuery(params);
        return properties.getPaymentUrl() + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    public CallbackVerification verify(Map<String, String> rawParams) {
        Map<String, String> params = sanitizedParams(rawParams);
        String supplied = rawParams == null ? null : rawParams.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) {
            return new CallbackVerification(false, params);
        }
        String expected = hmacSha512(canonicalQuery(params));
        boolean valid = MessageDigest.isEqual(
                expected.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
        return new CallbackVerification(valid, params);
    }

    public Map<String, String> sanitizedParams(Map<String, String> rawParams) {
        Map<String, String> sanitized = new TreeMap<>();
        if (rawParams == null) return sanitized;
        rawParams.forEach((key, value) -> {
            if (key != null && key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)
                    && value != null && !value.isEmpty()) {
                sanitized.put(key, value);
            }
        });
        return new LinkedHashMap<>(sanitized);
    }

    String canonicalQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(params).forEach((key, value) -> {
            if (value == null || value.isEmpty()) return;
            if (!builder.isEmpty()) builder.append('&');
            builder.append(encode(key)).append('=').append(encode(value));
        });
        return builder.toString();
    }

    String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign VNPay data.", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizedIp(String ip) {
        if (ip == null || ip.isBlank()) return "127.0.0.1";
        String first = ip.split(",", 2)[0].trim();
        return first.length() <= 45 ? first : first.substring(0, 45);
    }

    public record CallbackVerification(boolean valid, Map<String, String> params) { }
}
