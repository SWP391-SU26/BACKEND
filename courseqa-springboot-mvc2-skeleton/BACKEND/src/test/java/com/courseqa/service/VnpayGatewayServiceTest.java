package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.courseqa.config.VnpayProperties;
import com.courseqa.model.entity.PaymentOrder;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VnpayGatewayServiceTest {
    private VnpayGatewayService gateway;

    @BeforeEach
    void setUp() {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TEST1234");
        properties.setHashSecret("sandbox-secret-that-must-not-be-logged");
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://example.test/api/payments/vnpay/return");
        properties.setIpnUrl("https://example.test/api/payments/vnpay/ipn");
        properties.setFrontendReturnUrl("http://localhost:5173/payment/result");
        gateway = new VnpayGatewayService(properties);
    }

    @Test
    void generatedUrlUsesVersion210AmountTimesOneHundredAndValidHmac() {
        PaymentOrder order = order();

        Map<String, String> params = query(gateway.createPaymentUrl(order));

        assertEquals("2.1.0", params.get("vnp_Version"));
        assertEquals("4900000", params.get("vnp_Amount"));
        assertEquals("Thanh toan goi PRO_PLUS PRO-20260805-test", params.get("vnp_OrderInfo"));
        assertTrue(gateway.verify(params).valid());
        assertFalse(gateway.verify(params).params().containsKey("vnp_SecureHash"));
    }

    @Test
    void modifyingSignedAmountInvalidatesChecksum() {
        Map<String, String> params = query(gateway.createPaymentUrl(order()));
        params.put("vnp_Amount", "100");

        assertFalse(gateway.verify(params).valid());
    }

    private static PaymentOrder order() {
        PaymentOrder order = new PaymentOrder();
        order.setVnpTxnRef("PRO-20260805-test");
        order.setPlanCodeSnapshot("PRO_PLUS");
        order.setAmountVnd(49_000L);
        order.setClientIp("127.0.0.1");
        order.setCreatedAt(LocalDateTime.of(2026, 8, 5, 9, 0));
        order.setExpiresAt(LocalDateTime.of(2026, 8, 5, 9, 15));
        return order;
    }

    private static Map<String, String> query(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
