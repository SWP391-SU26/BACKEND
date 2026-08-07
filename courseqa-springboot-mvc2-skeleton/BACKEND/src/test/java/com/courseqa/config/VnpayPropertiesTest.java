package com.courseqa.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VnpayPropertiesTest {
    @Test
    void sandboxAllowsLocalhostReturnWithoutIpn() {
        VnpayProperties properties = configured("sandbox");
        properties.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        properties.setIpnUrl("");

        assertDoesNotThrow(properties::assertReady);
    }

    @Test
    void productionRequiresPublicHttpsReturnAndIpn() {
        VnpayProperties properties = configured("production");
        properties.setPaymentUrl("https://pay.vnpay.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        properties.setIpnUrl("");

        assertThrows(IllegalStateException.class, properties::assertReady);
    }

    @Test
    void productionAcceptsStablePublicCallbacks() {
        VnpayProperties properties = configured("production");
        properties.setPaymentUrl("https://pay.vnpay.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://api.example.com/api/payments/vnpay/return");
        properties.setIpnUrl("https://api.example.com/api/payments/vnpay/ipn");
        properties.setFrontendReturnUrl("https://app.example.com/payment/result");

        assertDoesNotThrow(properties::assertReady);
    }

    private static VnpayProperties configured(String environment) {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setEnvironment(environment);
        properties.setTmnCode("TEST");
        properties.setHashSecret("test-secret");
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setFrontendReturnUrl("http://localhost:5173/payment/result");
        return properties;
    }
}
