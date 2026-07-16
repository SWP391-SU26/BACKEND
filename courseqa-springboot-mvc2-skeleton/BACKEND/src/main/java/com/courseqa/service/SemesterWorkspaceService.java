package com.courseqa.service;

import com.courseqa.model.dto.*;
import com.courseqa.model.entity.*;
import com.courseqa.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SemesterWorkspaceService {
    private final SemesterWorkspaceRepository semesters;
    private final CourseRepository courses;

    public SemesterWorkspaceService(SemesterWorkspaceRepository semesters, CourseRepository courses) {
        this.semesters = semesters; this.courses = courses;
    }

    public List<SemesterDto.Response> list() {
        return semesters.findAllByOrderByCreatedAtDesc().stream().map(SemesterDto.Response::from).toList();
    }

    @Transactional
    public SemesterDto.Response create(SemesterDto.CreateRequest request, UUID creatorId) {
        if (request == null || blank(request.semesterName)) throw bad("semesterName is required.");
        LocalDateTime now = LocalDateTime.now();
        SemesterWorkspace semester = new SemesterWorkspace();
        semester.setSemesterCode("SEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        semester.setSemesterName(request.semesterName.trim());
        semester.setCreatedBy(creatorId);
        semester.setStatus("DRAFT");
        semester.setCreatedAt(now); semester.setUpdatedAt(now);
        return SemesterDto.Response.from(semesters.save(semester));
    }

    @Transactional
    public SemesterDto.Response update(UUID id, SemesterDto.UpdateRequest request) {
        SemesterWorkspace semester = get(id);
        if (request == null || blank(request.semesterName)) throw bad("semesterName is required.");
        semester.setSemesterName(request.semesterName.trim());
        semester.setUpdatedAt(LocalDateTime.now());
        return SemesterDto.Response.from(semesters.save(semester));
    }

    @Transactional
    public SemesterDto.Response status(UUID id, SemesterDto.StatusRequest request) {
        String status = request == null || request.status == null ? "" : request.status.trim().toUpperCase();
        if (!Set.of("DRAFT", "ACTIVE", "ARCHIVED").contains(status)) throw bad("Invalid semester status.");
        SemesterWorkspace semester = get(id);
        semester.setStatus(status); semester.setUpdatedAt(LocalDateTime.now());
        semesters.save(semester);
        if ("ARCHIVED".equals(status)) archiveCourses(id);
        return SemesterDto.Response.from(semester);
    }

    @Transactional
    public void archive(UUID id) {
        SemesterDto.StatusRequest request = new SemesterDto.StatusRequest(); request.status = "ARCHIVED";
        status(id, request);
    }

    public List<CourseDto.CourseResponse> courses(UUID id) {
        get(id);
        return courses.findBySemesterWorkspaceIdOrderByCreatedAtDesc(id).stream()
                .filter(course -> !"ARCHIVED".equals(course.getStatus()))
                .map(CourseDto.CourseResponse::fromEntity).toList();
    }

    private void archiveCourses(UUID semesterId) {
        LocalDateTime now = LocalDateTime.now();
        List<Course> children = courses.findBySemesterWorkspaceIdOrderByCreatedAtDesc(semesterId);
        children.forEach(course -> { course.setStatus("ARCHIVED"); course.setIsActive(false); course.setUpdatedAt(now); });
        courses.saveAll(children);
    }

    private SemesterWorkspace get(UUID id) {
        return semesters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester workspace not found."));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
