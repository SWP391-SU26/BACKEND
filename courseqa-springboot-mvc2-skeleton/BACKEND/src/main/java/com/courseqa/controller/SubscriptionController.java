package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.PaymentDto;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.SubscriptionService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@CrossOrigin
public class SubscriptionController {
    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping("/subscription")
    public ApiResponse<PaymentDto.SubscriptionResponse> current(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(subscriptions.current(principal.userId()));
    }

    @GetMapping("/subscription-history")
    public ApiResponse<List<PaymentDto.SubscriptionHistoryResponse>> history(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.ok(subscriptions.history(principal.userId()));
    }
}
