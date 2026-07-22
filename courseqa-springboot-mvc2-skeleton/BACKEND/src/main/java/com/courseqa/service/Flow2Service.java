package com.courseqa.service;

import com.courseqa.model.dto.CourseDto;
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
public class Flow2Service {
    private final CourseRepository courses;
    private final SemesterWorkspaceRepository semesters;
    private final CourseMembershipRepository memberships;
    private final CourseDocumentRepository documents;
    private final ChapterRepository chapters;
    private final UserRepository users;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentService documentService;

    public Flow2Service(CourseRepository courses, SemesterWorkspaceRepository semesters,
            CourseMembershipRepository memberships, CourseDocumentRepository documents,
            ChapterRepository chapters, UserRepository users, JdbcTemplate jdbcTemplate,
            DocumentService documentService) {
        this.courses = courses;
        this.semesters = semesters;
        this.memberships = memberships;
        this.documents = documents;
        this.chapters = chapters;
        this.users = users;
        this.jdbcTemplate = jdbcTemplate;
        this.documentService = documentService;
    }

    public List<CourseDto.CourseResponse> getMyCourses(UUID userId) {
        return courses.findByIsActiveTrueAndStatusNotOrderByCreatedAtDesc("ARCHIVED").stream()
                .filter(course -> course.getDeletedAt() == null)
                .filter(course -> course.getSemesterWorkspaceId() != null)
                .filter(course -> semesters.findById(course.getSemesterWorkspaceId())
                        .map(s -> s.getDeletedAt() == null && "ACTIVE".equals(s.getStatus())).orElse(false))
                .filter(course -> documents.existsByCourseIdAndProcessingStatusAndIndexingStatusAndDeletedAtIsNull(
                        course.getCourseId(), "PROCESSED", "INDEXED"))
                .map(CourseDto.CourseResponse::fromEntity).toList();
    }

    public void requireCourseAccess(UUID courseId, UUID userId, boolean admin) {
        Course course = getCourse(courseId);
        boolean visible = Boolean.TRUE.equals(course.getIsActive())
                && course.getDeletedAt() == null
                && !"ARCHIVED".equals(course.getStatus())
                && semesters.findById(course.getSemesterWorkspaceId()).map(s -> s.getDeletedAt() == null && "ACTIVE".equals(s.getStatus())).orElse(false)
                && documents.existsByCourseIdAndProcessingStatusAndIndexingStatusAndDeletedAtIsNull(
                        courseId, "PROCESSED", "INDEXED");
        if (!visible) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This course is not currently available.");
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(UUID id, CourseDto.UpdateCourseRequest request) {
        Course course = getCourse(id);
        if (request == null) throw bad("Update request is required.");
        if (!blank(request.courseCode)) course.setCourseCode(request.courseCode.trim());
        if (!blank(request.courseName)) course.setCourseName(request.courseName.trim());
        if (request.description != null) course.setDescription(request.description.trim());
        if (Boolean.TRUE.equals(request.isActive)) {
            if ("ARCHIVED".equals(course.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived courses cannot be activated.");
            }
            if (!documents.existsByCourseIdAndProcessingStatusAndIndexingStatusAndDeletedAtIsNull(id, "PROCESSED", "INDEXED")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Course needs at least one processed and indexed document before activation.");
            }
        }
        if (request.isActive != null) course.setIsActive(request.isActive);
        course.setUpdatedAt(LocalDateTime.now());
        return CourseDto.CourseResponse.fromEntity(courses.save(course));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourseStatus(UUID id, CourseDto.StatusRequest request) {
        Course course = getCourse(id);
        String status = request == null || request.status == null ? "" : request.status.trim().toUpperCase();
        if (!Set.of("DRAFT", "PUBLISHED").contains(status)) {
            throw bad("Invalid course status. Use DELETE to move a course to trash.");
        }
        if ("PUBLISHED".equals(status)) {
            CourseDto.PublishChecklistResponse checklist = publishChecklist(id);
            if (!checklist.canPublish) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Course cannot be published. Missing: " + String.join(", ", checklist.missing));
            }
        }
        course.setStatus(status);
        course.setIsActive(true);
        course.setUpdatedAt(LocalDateTime.now());
        return CourseDto.CourseResponse.fromEntity(courses.save(course));
    }

    @Transactional
    public void archiveCourse(UUID id, UUID deletedBy) {
        Course course = getCourse(id);
        course.setStatus("ARCHIVED");
        course.setIsActive(false);
        course.setDeletedAt(LocalDateTime.now());
        course.setDeletedBy(deletedBy);
        course.setUpdatedAt(LocalDateTime.now());
        courses.save(course);
        documents.findByCourseIdOrderByUploadedAtDesc(id).stream()
                .filter(document -> document.getDeletedAt() == null)
                .forEach(document -> {
                    document.setDeletedScope(document.getDocumentScope());
                    document.setDeletedReviewStatus(document.getReviewStatus());
                    document.setDeletedCourseId(document.getCourseId());
                    document.setDeletedWorkspaceId(document.getWorkspaceId());
                    document.setDeletedAt(LocalDateTime.now());
                    document.setDeletedBy(deletedBy);
                    documents.save(document);
                });
    }

    public List<CourseDto.CourseResponse> trash() {
        return courses.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .map(CourseDto.CourseResponse::fromEntity).toList();
    }

    @Transactional
    public CourseDto.CourseResponse restoreCourse(UUID id) {
        Course course = courses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        if (course.getDeletedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Course is not in trash.");
        SemesterWorkspace semester = semesters.findById(course.getSemesterWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found."));
        if (semester.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore the parent semester first.");
        }
        course.setStatus("DRAFT");
        course.setIsActive(false);
        course.setDeletedAt(null);
        course.setDeletedBy(null);
        course.setUpdatedAt(LocalDateTime.now());
        documents.findByCourseIdOrderByUploadedAtDesc(id).stream()
                .filter(document -> document.getDeletedAt() != null)
                .forEach(document -> {
                    document.setDeletedAt(null);
                    document.setDeletedBy(null);
                    document.setUpdatedAt(LocalDateTime.now());
                    documents.save(document);
                });
        return CourseDto.CourseResponse.fromEntity(courses.save(course));
    }

    @Transactional
    public void permanentlyDeleteCourse(UUID id, UUID requesterId) {
        Course course = courses.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
        if (course.getDeletedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Move the course to trash first.");
        Map<String, Long> dependencies = courseDependencies(id);
        if (dependencies.values().stream().anyMatch(count -> count > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course still has dependencies: " + dependencies);
        }
        for (CourseDocument document : documents.findByCourseIdOrderByUploadedAtDesc(id)) {
            documentService.permanentlyDeleteDocument(document.getDocumentId(), requesterId);
        }
        jdbcTemplate.update("UPDATE course_documents SET target_course_id = NULL WHERE target_course_id = ?", id);
        courses.delete(course);
    }

    public Map<String, Long> courseDependencies(UUID id) {
        long chatSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_sessions WHERE course_id = ?", Long.class, id);
        long datasets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_datasets WHERE course_id = ?", Long.class, id);
        long experiments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM experiments WHERE course_id = ?", Long.class, id);
        return new LinkedHashMap<>(Map.of("chatSessions", chatSessions, "frozenDatasets", datasets, "experiments", experiments));
    }

    public CourseDto.PublishChecklistResponse publishChecklist(UUID courseId) {
        Course course = getCourse(courseId);
        CourseDto.PublishChecklistResponse response = new CourseDto.PublishChecklistResponse();
        response.semesterActive = semesters.findById(course.getSemesterWorkspaceId())
                .map(s -> "ACTIVE".equals(s.getStatus())).orElse(false);
        response.processedDocument = documents.existsByCourseIdAndProcessingStatusAndIndexingStatusAndDeletedAtIsNull(
                courseId, "PROCESSED", "INDEXED");
        response.confirmedChapter = !chapters.findByCourseIdAndIsActiveTrueOrderByOrderIndexAsc(courseId).isEmpty();
        response.assignedStudent = memberships.findByCourseIdAndStatus(courseId, "ACTIVE").stream()
                .anyMatch(m -> "STUDENT".equalsIgnoreCase(m.getMembershipRole()));
        List<String> missing = new ArrayList<>();
        if (!response.semesterActive) missing.add("active semester");
        if (!response.processedDocument) missing.add("processed document");
        if (!response.confirmedChapter) missing.add("confirmed chapter");
        if (!response.assignedStudent) missing.add("assigned student");
        response.missing = missing;
        response.canPublish = missing.isEmpty();
        return response;
    }

    public List<CourseDto.MemberResponse> members(UUID courseId) {
        getCourse(courseId);
        return memberships.findByCourseIdAndStatus(courseId, "ACTIVE").stream().map(this::memberResponse).toList();
    }

    @Transactional
    public CourseDto.MemberResponse addMember(UUID courseId, CourseDto.MemberRequest request, UUID assignedBy) {
        getCourse(courseId);
        if (request == null || request.userId == null) throw bad("userId is required.");
        User user = users.findById(request.userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        CourseMembership member = memberships.findByCourseIdAndUserId(courseId, request.userId).orElseGet(CourseMembership::new);
        member.setCourseId(courseId);
        member.setUserId(user.getUserId());
        member.setMembershipRole(blank(request.membershipRole) ? "STUDENT" : request.membershipRole.trim().toUpperCase());
        if (!Set.of("STUDENT", "TEACHER").contains(member.getMembershipRole())) throw bad("Invalid membership role.");
        member.setStatus("ACTIVE");
        member.setAssignedBy(assignedBy);
        member.setAssignedAt(LocalDateTime.now());
        return memberResponse(memberships.save(member));
    }

    @Transactional
    public void removeMember(UUID courseId, UUID userId) {
        CourseMembership member = memberships.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found."));
        member.setStatus("REMOVED");
        memberships.save(member);
    }

    @Transactional
    public CourseDto.ChapterResponse updateChapter(UUID courseId, UUID chapterId, CourseDto.UpdateChapterRequest request) {
        Chapter chapter = chapters.findById(chapterId)
                .filter(item -> item.getCourseId().equals(courseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found."));
        if (!blank(request.chapterTitle)) chapter.setChapterTitle(request.chapterTitle.trim());
        if (request.description != null) chapter.setDescription(request.description.trim());
        if (request.orderIndex != null) chapter.setOrderIndex(request.orderIndex);
        if (request.isActive != null) chapter.setIsActive(request.isActive);
        chapter.setUpdatedAt(LocalDateTime.now());
        return CourseDto.ChapterResponse.fromEntity(chapters.save(chapter));
    }

    @Transactional
    public void deactivateChapter(UUID courseId, UUID chapterId) {
        CourseDto.UpdateChapterRequest request = new CourseDto.UpdateChapterRequest();
        request.isActive = false;
        updateChapter(courseId, chapterId, request);
    }

    private CourseDto.MemberResponse memberResponse(CourseMembership member) {
        CourseDto.MemberResponse response = new CourseDto.MemberResponse();
        response.courseMembershipId = member.getCourseMembershipId();
        response.userId = member.getUserId();
        users.findById(member.getUserId()).ifPresent(user -> {
            response.fullName = user.getFullName(); response.email = user.getEmail();
        });
        response.membershipRole = member.getMembershipRole();
        response.status = member.getStatus();
        response.assignedAt = member.getAssignedAt();
        return response;
    }

    private Course getCourse(UUID id) {
        return courses.findById(id).filter(course -> course.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
