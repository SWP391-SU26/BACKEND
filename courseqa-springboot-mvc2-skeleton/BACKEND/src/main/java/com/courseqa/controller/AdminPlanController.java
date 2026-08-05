package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.PaymentDto;
import com.courseqa.service.PlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/plans")
@CrossOrigin
public class AdminPlanController {
    private final PlanService plans;

    public AdminPlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public ApiResponse<List<PaymentDto.PlanResponse>> list() {
        return ApiResponse.ok(plans.getAllPlans());
    }

    @PostMapping
    public ApiResponse<PaymentDto.PlanResponse> create(@Valid @RequestBody PaymentDto.PlanUpsertRequest request) {
        return ApiResponse.ok(plans.create(request));
    }

    @PutMapping("/{planId}")
    public ApiResponse<PaymentDto.PlanResponse> update(
            @PathVariable UUID planId,
            @Valid @RequestBody PaymentDto.PlanUpsertRequest request
    ) {
        return ApiResponse.ok(plans.update(planId, request));
    }

    @DeleteMapping("/{planId}")
    public ApiResponse<PaymentDto.PlanDeleteResponse> delete(@PathVariable UUID planId) {
        return ApiResponse.ok(plans.delete(planId));
    }
}
