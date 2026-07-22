package com.courseqa.service;

import com.courseqa.model.dto.*;
import com.courseqa.model.entity.*;
import com.courseqa.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SemesterWorkspaceService {
    private final SemesterWorkspaceRepository semesters;
    private final CourseRepository courses;
    private final Flow2Service flow2Service;
    private final JdbcTemplate jdbcTemplate;

    public SemesterWorkspaceService(SemesterWorkspaceRepository semesters, CourseRepository courses,
            Flow2Service flow2Service, JdbcTemplate jdbcTemplate) {
        this.semesters = semesters; this.courses = courses; this.flow2Service = flow2Service; this.jdbcTemplate = jdbcTemplate;
    }

    public List<SemesterDto.Response> list() {
        return semesters.findByDeletedAtIsNullOrderByCreatedAtDesc().stream().map(SemesterDto.Response::from).toList();
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
        if (!Set.of("DRAFT", "ACTIVE").contains(status)) {
            throw bad("Invalid semester status. Use DELETE to move a semester to trash.");
        }
        SemesterWorkspace semester = get(id);
        semester.setStatus(status); semester.setUpdatedAt(LocalDateTime.now());
        semesters.save(semester);
        return SemesterDto.Response.from(semester);
    }

    @Transactional
    public void archive(UUID id, UUID deletedBy) {
        SemesterWorkspace semester = get(id);
        semester.setStatus("ARCHIVED");
        semester.setDeletedAt(LocalDateTime.now());
        semester.setDeletedBy(deletedBy);
        semester.setUpdatedAt(LocalDateTime.now());
        semesters.save(semester);
        courses.findBySemesterWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(id)
                .forEach(course -> flow2Service.archiveCourse(course.getCourseId(), deletedBy));
    }

    public List<SemesterDto.Response> trash() {
        return semesters.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .map(SemesterDto.Response::from).toList();
    }

    @Transactional
    public SemesterDto.Response restore(UUID id) {
        SemesterWorkspace semester = semesters.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester workspace not found."));
        if (semester.getDeletedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Semester is not in trash.");
        semester.setStatus("DRAFT");
        semester.setDeletedAt(null);
        semester.setDeletedBy(null);
        semester.setUpdatedAt(LocalDateTime.now());
        return SemesterDto.Response.from(semesters.save(semester));
    }

    @Transactional
    public void permanentlyDelete(UUID id, UUID requesterId) {
        SemesterWorkspace semester = semesters.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester workspace not found."));
        if (semester.getDeletedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Move the semester to trash first.");
        List<Course> children = courses.findBySemesterWorkspaceIdOrderByCreatedAtDesc(id);
        long chatSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE semester_workspace_id = ?", Long.class, id);
        long datasets = children.stream().mapToLong(course -> flow2Service.courseDependencies(course.getCourseId()).get("frozenDatasets")).sum();
        long experiments = children.stream().mapToLong(course -> flow2Service.courseDependencies(course.getCourseId()).get("experiments")).sum();
        long courseChats = children.stream().mapToLong(course -> flow2Service.courseDependencies(course.getCourseId()).get("chatSessions")).sum();
        Map<String, Long> dependencies = new LinkedHashMap<>(Map.of(
                "chatSessions", chatSessions + courseChats, "frozenDatasets", datasets, "experiments", experiments));
        if (dependencies.values().stream().anyMatch(count -> count > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Semester still has dependencies: " + dependencies);
        }
        for (Course course : children) flow2Service.permanentlyDeleteCourse(course.getCourseId(), requesterId);
        semesters.delete(semester);
    }

    public List<CourseDto.CourseResponse> courses(UUID id) {
        get(id);
        return courses.findBySemesterWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(id).stream()
                .map(CourseDto.CourseResponse::fromEntity).toList();
    }

    private void archiveCourses(UUID semesterId) {
        LocalDateTime now = LocalDateTime.now();
        List<Course> children = courses.findBySemesterWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(semesterId);
        children.forEach(course -> { course.setStatus("ARCHIVED"); course.setIsActive(false); course.setUpdatedAt(now); });
        courses.saveAll(children);
    }

    private SemesterWorkspace get(UUID id) {
        return semesters.findById(id).filter(semester -> semester.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester workspace not found."));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
