package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.PaymentDto;
import com.courseqa.service.PlanService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
@CrossOrigin
public class PlanController {
    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public ApiResponse<List<PaymentDto.PlanResponse>> list() {
        return ApiResponse.ok(plans.getActivePlans());
    }
}
