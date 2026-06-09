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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<List<CourseDto.CourseResponse>> getCourses() {
        return ApiResponse.ok(courseService.getCourses());
    }

    @PostMapping
    public ApiResponse<CourseDto.CourseResponse> createCourse(@RequestBody CourseDto.CreateCourseRequest request) {
        return ApiResponse.ok(courseService.createCourse(request));
    }

    @GetMapping("/workspaces")
    public ApiResponse<List<CourseDto.WorkspaceResponse>> getAllWorkspaces() {
        return ApiResponse.ok(courseService.getAllWorkspaces());
    }

    @GetMapping("/{courseId}/workspaces")
    public ApiResponse<List<CourseDto.WorkspaceResponse>> getWorkspaces(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseService.getWorkspaces(courseId));
    }

    @PostMapping("/{courseId}/workspaces")
    public ApiResponse<CourseDto.WorkspaceResponse> createWorkspace(
            @PathVariable UUID courseId,
            @RequestBody CourseDto.CreateWorkspaceRequest request
    ) {
        return ApiResponse.ok(courseService.createWorkspace(courseId, request));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<CourseDto.ChapterResponse>> getChapters(@PathVariable UUID courseId) {
        return ApiResponse.ok(courseService.getChapters(courseId));
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<CourseDto.ChapterResponse> createChapter(
            @PathVariable UUID courseId,
            @RequestBody CourseDto.CreateChapterRequest request
    ) {
        return ApiResponse.ok(courseService.createChapter(courseId, request));
    }
}
