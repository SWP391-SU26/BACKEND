package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.CourseDto;
import com.courseqa.service.CourseService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    // ── helper: extract userId from "Authorization: Bearer <userId>" ──
    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header.");
        }
        try {
            return UUID.fromString(authHeader.substring(7).trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token.");
        }
    }

    // ── GET endpoints (no auth needed) ──
    @GetMapping
    public ApiResponse<List<CourseDto.CourseResponse>> getCourses() {
        return ApiResponse.ok(courseService.getCourses());
    }

    @GetMapping("/workspaces")
    public ApiResponse<List<CourseDto.WorkspaceResponse>> getAllWorkspaces() {
        return ApiResponse.ok(courseService.getAllWorkspaces());
    }

    @GetMapping("/{courseId}/workspaces")
    public ApiResponse<List<CourseDto.WorkspaceResponse>> getWorkspaces(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseService.getWorkspaces(courseId));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<CourseDto.ChapterResponse>> getChapters(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseService.getChapters(courseId));
    }

    // ── POST endpoints (require Teacher/Admin token) ──
    @PostMapping
    public ApiResponse<CourseDto.CourseResponse> createCourse(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody CourseDto.CreateCourseRequest request) {
        return ApiResponse.ok(courseService.createCourse(extractUserId(auth), request));
    }

    @PostMapping("/{courseId}/workspaces")
    public ApiResponse<CourseDto.WorkspaceResponse> createWorkspace(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable UUID courseId,
            @RequestBody CourseDto.CreateWorkspaceRequest request) {
        return ApiResponse.ok(courseService.createWorkspace(extractUserId(auth), courseId, request));
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<CourseDto.ChapterResponse> createChapter(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable UUID courseId,
            @RequestBody CourseDto.CreateChapterRequest request) {
        return ApiResponse.ok(courseService.createChapter(extractUserId(auth), courseId, request));
    }
}