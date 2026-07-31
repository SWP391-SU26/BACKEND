package com.courseqa.service;

import com.courseqa.model.dto.LearningScopeDto;
import com.courseqa.model.entity.*;
import com.courseqa.repository.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LearningScopeService {
    private final SemesterWorkspaceRepository semesters;
    private final CourseRepository courses;
    private final CourseWorkspaceRepository workspaces;
    private final CourseDocumentRepository documents;
    private final ChapterRepository chapters;
    private final DocumentChapterRangeRepository ranges;
    private final UserRepository users;

    public LearningScopeService(SemesterWorkspaceRepository semesters, CourseRepository courses,
            CourseWorkspaceRepository workspaces,
            CourseDocumentRepository documents, ChapterRepository chapters,
            DocumentChapterRangeRepository ranges, UserRepository users) {
        this.semesters = semesters; this.courses = courses;
        this.workspaces = workspaces; this.documents = documents; this.chapters = chapters; this.ranges = ranges;
        this.users = users;
    }

    public List<LearningScopeDto.SemesterScope> scope(UUID userId, boolean admin) {
        Map<UUID, SemesterWorkspace> semesterById = semesters.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(SemesterWorkspace::getSemesterWorkspaceId, Function.identity()));
        List<Course> visibleCourses = courses.findAll().stream()
                .filter(course -> visible(course, semesterById.get(course.getSemesterWorkspaceId())))
                .sorted(Comparator.comparing(Course::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Set<UUID> creatorIds = new HashSet<>();
        semesterById.values().stream().map(SemesterWorkspace::getCreatedBy)
                .filter(Objects::nonNull).forEach(creatorIds::add);
        visibleCourses.stream().map(Course::getCreatedBy)
                .filter(Objects::nonNull).forEach(creatorIds::add);
        Map<UUID, String> creatorNames = users.findAllById(creatorIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, User::getFullName));
        Map<UUID, LearningScopeDto.SemesterScope> result = new LinkedHashMap<>();

        visibleCourses.forEach(course -> {
            SemesterWorkspace semester = semesterById.get(course.getSemesterWorkspaceId());
            List<CourseDocument> courseDocuments = documents.findByCourseIdOrderByUploadedAtDesc(course.getCourseId());
            courseDocuments = courseDocuments.stream().filter(this::isSharedDocument).toList();
            long processed = courseDocuments.stream().filter(doc -> "PROCESSED".equals(doc.getProcessingStatus())).count();
            if (processed == 0) return;
            CourseWorkspace workspace = workspaces.findByCourseIdOrderByCreatedAtDesc(course.getCourseId()).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getIsActive())).findFirst().orElse(null);
            if (workspace == null) return;

            LearningScopeDto.SemesterScope semesterScope = result.computeIfAbsent(semester.getSemesterWorkspaceId(), id -> {
                LearningScopeDto.SemesterScope value = new LearningScopeDto.SemesterScope();
                value.semesterId = id; value.semesterCode = semester.getSemesterCode();
                value.semesterName = semester.getSemesterName(); value.status = semester.getStatus();
                value.createdBy = semester.getCreatedBy();
                value.creatorName = creatorNames.get(semester.getCreatedBy());
                value.createdAt = semester.getCreatedAt();
                return value;
            });
            LearningScopeDto.CourseScope courseScope = new LearningScopeDto.CourseScope();
            courseScope.courseId = course.getCourseId(); courseScope.courseCode = course.getCourseCode();
            courseScope.courseName = course.getCourseName(); courseScope.status = course.getStatus();
            courseScope.createdBy = course.getCreatedBy();
            courseScope.creatorName = creatorNames.get(course.getCreatedBy());
            courseScope.createdAt = course.getCreatedAt();
            courseScope.workspaceId = workspace.getWorkspaceId(); courseScope.documentCount = courseDocuments.size();
            courseScope.processedDocumentCount = (int) processed;
            semesterScope.courses.add(courseScope);
        });
        return result.values().stream().sorted(Comparator.comparing(item -> item.semesterName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public LearningScopeDto.CourseMaterials materials(UUID courseId, UUID userId, boolean admin) {
        Course course = requireAccessibleCourse(courseId, userId, admin);
        LearningScopeDto.CourseMaterials response = new LearningScopeDto.CourseMaterials(); response.courseId = courseId;
        Map<UUID, LearningScopeDto.ChapterMaterials> chapterGroups = new LinkedHashMap<>();
        for (Chapter chapter : chapters.findByCourseIdAndIsActiveTrueOrderByOrderIndexAsc(courseId)) {
            LearningScopeDto.ChapterMaterials group = new LearningScopeDto.ChapterMaterials();
            group.chapterId = chapter.getChapterId(); group.chapterTitle = chapter.getChapterTitle(); group.orderIndex = chapter.getOrderIndex();
            chapterGroups.put(chapter.getChapterId(), group); response.chapters.add(group);
        }
        for (CourseDocument document : documents.findByCourseIdOrderByUploadedAtDesc(courseId).stream()
                .filter(this::isSharedDocument).toList()) {
            List<DocumentChapterRange> documentRanges = ranges.findByDocumentIdOrderByPageStartAsc(document.getDocumentId());
            for (DocumentChapterRange range : documentRanges) {
                LearningScopeDto.ChapterMaterials group = chapterGroups.get(range.getChapterId());
                if (group != null) group.materials.add(toMaterial(document, range.getPageStart(), range.getPageEnd()));
            }
            addUnclassified(response.unclassifiedMaterials, document, documentRanges);
        }
        response.chapters.removeIf(group -> group.materials.isEmpty());
        return response;
    }

    public Course requireAccessibleCourse(UUID courseId, UUID userId, boolean admin) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        SemesterWorkspace semester = semesters.findById(course.getSemesterWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found."));
        if (!visible(course, semester)
                || !documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                        courseId, "PROCESSED", "INDEXED")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not currently available.");
        }
        return course;
    }

    public SemesterWorkspace requireAccessibleSemester(UUID semesterId) {
        SemesterWorkspace semester = semesters.findById(semesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found."));
        if (!"ACTIVE".equals(semester.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This semester is not currently available.");
        }
        return semester;
    }

    public List<Course> accessibleCoursesInSemester(UUID semesterId, UUID userId, boolean admin) {
        SemesterWorkspace semester = requireAccessibleSemester(semesterId);
        return courses.findBySemesterWorkspaceIdOrderByCreatedAtDesc(semesterId).stream()
                .filter(course -> visible(course, semester))
                .filter(course -> documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                        course.getCourseId(), "PROCESSED", "INDEXED"))
                .toList();
    }

    public CourseWorkspace requireActiveWorkspace(UUID courseId) {
        return workspaces.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "This course has no active knowledge-base workspace."));
    }

    private boolean visible(Course course, SemesterWorkspace semester) {
        return semester != null
                && Boolean.TRUE.equals(course.getIsActive())
                && !"ARCHIVED".equals(course.getStatus())
                && "ACTIVE".equals(semester.getStatus());
    }

    private boolean isSharedDocument(CourseDocument document) {
        return (document.getDocumentScope() == null || "COURSE".equals(document.getDocumentScope()))
                && (document.getReviewStatus() == null || "APPROVED".equals(document.getReviewStatus()));
    }

    private void addUnclassified(List<LearningScopeDto.Material> output, CourseDocument document, List<DocumentChapterRange> ranges) {
        int total = Optional.ofNullable(document.getTotalPages()).orElse(0);
        if (ranges.isEmpty() || total < 1) { output.add(toMaterial(document, total > 0 ? 1 : null, total > 0 ? total : null)); return; }
        int cursor = 1;
        for (DocumentChapterRange range : ranges) {
            if (range.getPageStart() > cursor) output.add(toMaterial(document, cursor, range.getPageStart() - 1));
            cursor = Math.max(cursor, range.getPageEnd() + 1);
        }
        if (cursor <= total) output.add(toMaterial(document, cursor, total));
    }

    private LearningScopeDto.Material toMaterial(CourseDocument document, Integer start, Integer end) {
        LearningScopeDto.Material item = new LearningScopeDto.Material(); item.documentId = document.getDocumentId();
        item.documentTitle = document.getDocumentTitle(); item.originalFilename = document.getOriginalFilename();
        item.processingStatus = document.getProcessingStatus(); item.pageStart = start; item.pageEnd = end;
        item.totalPages = document.getTotalPages(); return item;
    }
}
