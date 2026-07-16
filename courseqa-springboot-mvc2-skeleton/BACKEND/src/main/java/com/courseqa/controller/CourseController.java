package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.CourseDto;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.CourseService;
import com.courseqa.service.Flow2Service;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin
public class CourseController {
    private final CourseService courseService;
    private final Flow2Service flow2Service;

    public CourseController(CourseService courseService, Flow2Service flow2Service) {
        this.courseService = courseService;
        this.flow2Service = flow2Service;
    }

    @GetMapping
    public ApiResponse<List<CourseDto.CourseResponse>> getCourses() {
        return ApiResponse.ok(courseService.getCourses());
    }

    @GetMapping("/my")
    public ApiResponse<List<CourseDto.CourseResponse>> getMyCourses(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(flow2Service.getMyCourses(principal.userId()));
    }

    @GetMapping("/workspaces")
    public ApiResponse<List<CourseDto.WorkspaceResponse>> getAllWorkspaces() {
        return ApiResponse.ok(courseService.getAllWorkspaces());
    }

    @GetMapping("/{courseId}/workspaces")
    public ApiResponse<List<CourseDto.WorkspaceResponse>> getWorkspaces(@PathVariable UUID courseId, @AuthenticationPrincipal JwtPrincipal principal) {
        flow2Service.requireCourseAccess(courseId, principal.userId(), principal.roles().contains("ADMIN"));
        return ApiResponse.ok(courseService.getWorkspaces(courseId));
    }

    @GetMapping("/{courseId}/chapters")
    public ApiResponse<List<CourseDto.ChapterResponse>> getChapters(@PathVariable UUID courseId, @AuthenticationPrincipal JwtPrincipal principal) {
        flow2Service.requireCourseAccess(courseId, principal.userId(), principal.roles().contains("ADMIN"));
        return ApiResponse.ok(courseService.getChapters(courseId));
    }

    @GetMapping("/{courseId}/publish-checklist")
    public ApiResponse<CourseDto.PublishChecklistResponse> publishChecklist(@PathVariable UUID courseId) {
        return ApiResponse.ok(flow2Service.publishChecklist(courseId));
    }

    @PostMapping
    public ApiResponse<CourseDto.CourseResponse> createCourse(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody CourseDto.CreateCourseRequest request) {
        return ApiResponse.ok(courseService.createCourse(principal.userId(), request));
    }

    @PostMapping("/semester/{semesterId}")
    public ApiResponse<CourseDto.CourseResponse> createCourseForSemester(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID semesterId,
            @RequestBody CourseDto.CreateCourseRequest request) {
        request.semesterWorkspaceId = semesterId;
        return ApiResponse.ok(courseService.createCourse(principal.userId(), request));
    }

    @PatchMapping("/{courseId}")
    public ApiResponse<CourseDto.CourseResponse> updateCourse(
            @PathVariable UUID courseId, @RequestBody CourseDto.UpdateCourseRequest request) {
        return ApiResponse.ok(flow2Service.updateCourse(courseId, request));
    }

    @PatchMapping("/{courseId}/status")
    public ApiResponse<CourseDto.CourseResponse> updateCourseStatus(
            @PathVariable UUID courseId, @RequestBody CourseDto.StatusRequest request) {
        return ApiResponse.ok(flow2Service.updateCourseStatus(courseId, request));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> deleteCourse(@PathVariable UUID courseId) {
        flow2Service.archiveCourse(courseId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{courseId}/members")
    public ApiResponse<List<CourseDto.MemberResponse>> members(@PathVariable UUID courseId) {
        return ApiResponse.ok(flow2Service.members(courseId));
    }

    @PostMapping("/{courseId}/members")
    public ApiResponse<CourseDto.MemberResponse> addMember(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID courseId,
            @RequestBody CourseDto.MemberRequest request) {
        return ApiResponse.ok(flow2Service.addMember(courseId, request, principal.userId()));
    }

    @DeleteMapping("/{courseId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable UUID courseId, @PathVariable UUID userId) {
        flow2Service.removeMember(courseId, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{courseId}/workspaces")
    public ApiResponse<CourseDto.WorkspaceResponse> createWorkspace(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID courseId,
            @RequestBody CourseDto.CreateWorkspaceRequest request) {
        return ApiResponse.ok(courseService.createWorkspace(principal.userId(), courseId, request));
    }

    @PostMapping("/{courseId}/chapters")
    public ApiResponse<CourseDto.ChapterResponse> createChapter(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID courseId,
            @RequestBody CourseDto.CreateChapterRequest request) {
        return ApiResponse.ok(courseService.createChapter(principal.userId(), courseId, request));
    }

    @PatchMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<CourseDto.ChapterResponse> updateChapter(
            @PathVariable UUID courseId, @PathVariable UUID chapterId,
            @RequestBody CourseDto.UpdateChapterRequest request) {
        return ApiResponse.ok(flow2Service.updateChapter(courseId, chapterId, request));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable UUID courseId, @PathVariable UUID chapterId) {
        flow2Service.deactivateChapter(courseId, chapterId);
        return ApiResponse.ok(null);
    }
}
