package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.CourseDto;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.PersonalWorkspaceService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/personal-workspaces")
@CrossOrigin
public class PersonalWorkspaceController {
    private final PersonalWorkspaceService workspaces;

    public PersonalWorkspaceController(PersonalWorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @GetMapping
    public ApiResponse<List<CourseDto.WorkspaceResponse>> list(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(workspaces.list(principal.userId()).stream()
                .map(CourseDto.WorkspaceResponse::fromEntity).toList());
    }

    @PostMapping
    public ApiResponse<CourseDto.WorkspaceResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody CourseDto.CreateWorkspaceRequest request
    ) {
        return ApiResponse.ok(CourseDto.WorkspaceResponse.fromEntity(
                workspaces.create(principal.userId(), request.workspaceTitle, request.description)));
    }
}
