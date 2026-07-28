package com.courseqa.controller;

import com.courseqa.model.dto.AdminDashboardDto;
import com.courseqa.model.dto.ApiResponse;
import com.courseqa.service.AdminDashboardService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin
public class AdminDashboardController {
    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminDashboardDto.SummaryResponse> summary() {
        return ApiResponse.ok(service.summary());
    }

    @GetMapping("/timeseries")
    public ApiResponse<AdminDashboardDto.TimeseriesResponse> timeseries(
            @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(service.timeseries(days));
    }

    @GetMapping("/health")
    public ApiResponse<AdminDashboardDto.HealthResponse> health() {
        return ApiResponse.ok(service.health());
    }
}
