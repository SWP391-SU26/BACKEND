package com.courseqa.controller;

import com.courseqa.config.VnpayProperties;
import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.PaymentDto;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin
public class PaymentController {
    private final PaymentService payments;
    private final VnpayProperties properties;

    public PaymentController(PaymentService payments, VnpayProperties properties) {
        this.payments = payments;
        this.properties = properties;
    }

    @PostMapping("/vnpay/orders")
    public ResponseEntity<ApiResponse<PaymentDto.CreateOrderResponse>> createOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody(required = false) PaymentDto.CreateOrderRequest request,
            HttpServletRequest servletRequest
    ) {
        String planCode = request == null ? "PRO" : request.planCode;
        PaymentDto.CreateOrderResponse response = payments.createOrder(
                principal.userId(), planCode, clientIp(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentDto.OrderResponse> order(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID orderId
    ) {
        return ApiResponse.ok(payments.getOwnedOrder(orderId, principal.userId()));
    }

    @GetMapping("/orders")
    public ApiResponse<PaymentDto.UserPaymentPageResponse> orders(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(payments.userPayments(principal.userId(), status, page, size));
    }

    @GetMapping("/vnpay/ipn")
    public PaymentDto.IpnResponse ipn(
            @RequestParam Map<String, String> params,
            HttpServletRequest request
    ) {
        return payments.handleIpn(params, clientIp(request));
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> vnpayReturn(
            @RequestParam Map<String, String> params,
            HttpServletRequest request
    ) {
        PaymentService.ReturnResult result = payments.inspectReturn(params, clientIp(request));
        UriComponentsBuilder redirect = UriComponentsBuilder
                .fromUriString(properties.getFrontendReturnUrl())
                .queryParam("returnStatus", result.valid() ? "verified" : "invalid");
        if (result.orderId() != null) redirect.queryParam("orderId", result.orderId());
        if (!result.valid() && result.error() != null) redirect.queryParam("reason", result.error());
        URI location = redirect.build(true).toUri();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded;
    }
}
