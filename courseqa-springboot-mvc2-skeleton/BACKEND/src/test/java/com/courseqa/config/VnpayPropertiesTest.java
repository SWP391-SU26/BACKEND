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

    @Test
    void rejectsAPlaceholderMerchantCodeBeforeRedirectingToVnpay() {
        VnpayProperties properties = configured("sandbox");
        properties.setTmnCode("your_vnpay_tmn_code_here");

        assertThrows(IllegalStateException.class, properties::assertReady);
    }

    @Test
    void rejectsMerchantCodesThatDoNotMatchVnpayFormat() {
        VnpayProperties properties = configured("sandbox");
        properties.setTmnCode("TOO-LONG-CODE");

        assertThrows(IllegalStateException.class, properties::assertReady);
    }

    @Test
    void rejectsAPlaceholderHashSecretBeforeRedirectingToVnpay() {
        VnpayProperties properties = configured("sandbox");
        properties.setHashSecret("your_vnpay_hash_secret_here");

        assertThrows(IllegalStateException.class, properties::assertReady);
    }

    private static VnpayProperties configured(String environment) {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setEnvironment(environment);
        properties.setTmnCode("TEST1234");
        properties.setHashSecret("8f2d7a4c9b6e1f305d8c2a7b4e9f1063");
        properties.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setFrontendReturnUrl("http://localhost:5173/payment/result");
        return properties;
    }
}
