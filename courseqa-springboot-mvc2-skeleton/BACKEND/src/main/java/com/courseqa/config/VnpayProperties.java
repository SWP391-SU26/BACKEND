package com.courseqa.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnpayProperties {
    private boolean enabled;
    private String environment = "sandbox";
    private String tmnCode = "";
    private String hashSecret = "";
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String returnUrl = "";
    private String ipnUrl = "";
    private String frontendReturnUrl = "http://localhost:5173/payment/result";
    private int orderTtlMinutes = 15;

    @PostConstruct
    public void validateWhenEnabled() {
        if (enabled) assertReady();
    }

    public void assertReady() {
        if (!enabled) {
            throw new IllegalStateException("VNPay payment is disabled.");
        }
        String normalizedEnvironment = environment == null ? "" : environment.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"sandbox".equals(normalizedEnvironment) && !"production".equals(normalizedEnvironment)) {
            throw new IllegalStateException("VNPAY_ENVIRONMENT must be sandbox or production.");
        }
        requireTmnCode(tmnCode);
        requireCredential(hashSecret, "VNPAY_HASH_SECRET");
        require(paymentUrl, "VNPAY_PAYMENT_URL");
        require(returnUrl, "VNPAY_RETURN_URL");
        require(frontendReturnUrl, "FRONTEND_PAYMENT_RETURN_URL");
        if ("sandbox".equals(normalizedEnvironment) && !paymentUrl.contains("sandbox.vnpayment.vn")) {
            throw new IllegalStateException("Sandbox must use a VNPay Sandbox payment URL.");
        }
        if ("production".equals(normalizedEnvironment) && paymentUrl.contains("sandbox.vnpayment.vn")) {
            throw new IllegalStateException("Production must not use the VNPay Sandbox payment URL.");
        }
        requireHttpUrl(returnUrl, "VNPAY_RETURN_URL");
        requireHttpUrl(frontendReturnUrl, "FRONTEND_PAYMENT_RETURN_URL");
        if ("production".equals(normalizedEnvironment)) {
            require(ipnUrl, "VNPAY_IPN_URL");
            requirePublicHttps(returnUrl, "VNPAY_RETURN_URL");
            requirePublicHttps(ipnUrl, "VNPAY_IPN_URL");
        } else if (ipnUrl != null && !ipnUrl.isBlank()) {
            requireHttpUrl(ipnUrl, "VNPAY_IPN_URL");
        }
        if (orderTtlMinutes < 5 || orderTtlMinutes > 60) {
            throw new IllegalStateException("VNPAY_ORDER_TTL_MINUTES must be between 5 and 60.");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank() || value.contains("replace-with")) {
            throw new IllegalStateException(name + " must be configured when VNPay is enabled.");
        }
    }

    private static void requireTmnCode(String value) {
        requireCredential(value, "VNPAY_TMN_CODE");
        if (!value.trim().matches("[A-Za-z0-9]{8}")) {
            throw new IllegalStateException(
                    "VNPAY_TMN_CODE must be the 8-character alphanumeric website code issued by VNPay.");
        }
    }

    private static void requireCredential(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when VNPay is enabled.");
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("replace-with")
                || normalized.contains("placeholder")
                || normalized.contains("your_")
                || normalized.contains("your-")
                || normalized.contains("tmn_code")
                || normalized.contains("hash_secret")
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("example")
                || normalized.contains("sample")) {
            throw new IllegalStateException(name + " still contains a placeholder value.");
        }
    }

    private static void requirePublicHttps(String value, String name) {
        URI uri = parseUrl(value, name);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
            throw new IllegalStateException(name + " must be a public HTTPS URL.");
        }
    }

    private static void requireHttpUrl(String value, String name) {
        URI uri = parseUrl(value, name);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException(name + " must be an absolute HTTP(S) URL.");
        }
    }

    private static URI parseUrl(String value, String name) {
        try {
            return URI.create(value.trim());
        } catch (Exception exception) {
            throw new IllegalStateException(name + " must be a valid URL.");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public String getIpnUrl() { return ipnUrl; }
    public void setIpnUrl(String ipnUrl) { this.ipnUrl = ipnUrl; }
    public String getFrontendReturnUrl() { return frontendReturnUrl; }
    public void setFrontendReturnUrl(String frontendReturnUrl) { this.frontendReturnUrl = frontendReturnUrl; }
    public int getOrderTtlMinutes() { return orderTtlMinutes; }
    public void setOrderTtlMinutes(int orderTtlMinutes) { this.orderTtlMinutes = orderTtlMinutes; }
}
