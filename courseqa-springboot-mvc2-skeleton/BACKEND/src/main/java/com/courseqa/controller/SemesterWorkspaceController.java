package com.courseqa.controller;

import com.courseqa.model.dto.*;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.SemesterWorkspaceService;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/semester-workspaces")
@CrossOrigin
public class SemesterWorkspaceController {
    private final SemesterWorkspaceService service;
    public SemesterWorkspaceController(SemesterWorkspaceService service) { this.service = service; }

    @GetMapping public ApiResponse<List<SemesterDto.Response>> list() { return ApiResponse.ok(service.list()); }
    @PostMapping public ApiResponse<SemesterDto.Response> create(
            @AuthenticationPrincipal JwtPrincipal principal, @RequestBody SemesterDto.CreateRequest request) {
        return ApiResponse.ok(service.create(request, principal.userId()));
    }
    @PatchMapping("/{id}") public ApiResponse<SemesterDto.Response> update(
            @PathVariable UUID id, @RequestBody SemesterDto.UpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }
    @PatchMapping("/{id}/status") public ApiResponse<SemesterDto.Response> status(
            @PathVariable UUID id, @RequestBody SemesterDto.StatusRequest request) {
        return ApiResponse.ok(service.status(id, request));
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> archive(@PathVariable UUID id) {
        service.archive(id); return ApiResponse.ok(null);
    }
    @GetMapping("/{id}/courses") public ApiResponse<List<CourseDto.CourseResponse>> courses(@PathVariable UUID id) {
        return ApiResponse.ok(service.courses(id));
    }
}
