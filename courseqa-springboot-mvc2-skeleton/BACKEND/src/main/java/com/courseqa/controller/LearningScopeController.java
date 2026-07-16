package com.courseqa.controller;

import com.courseqa.model.dto.*;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.LearningScopeService;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class LearningScopeController {
    private final LearningScopeService service;
    public LearningScopeController(LearningScopeService service) { this.service = service; }

    @GetMapping("/learning-scope")
    public ApiResponse<List<LearningScopeDto.SemesterScope>> scope(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(service.scope(principal.userId(), principal.roles().contains("ADMIN")));
    }

    @GetMapping("/courses/{courseId}/materials")
    public ApiResponse<LearningScopeDto.CourseMaterials> materials(
            @PathVariable UUID courseId, @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(service.materials(courseId, principal.userId(), principal.roles().contains("ADMIN")));
    }
}
