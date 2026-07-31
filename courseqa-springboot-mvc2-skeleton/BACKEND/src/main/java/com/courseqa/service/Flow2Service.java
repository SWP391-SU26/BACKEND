package com.courseqa.service;

import com.courseqa.model.dto.CourseDto;
import com.courseqa.model.entity.*;
import com.courseqa.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
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

    public Flow2Service(CourseRepository courses, SemesterWorkspaceRepository semesters,
            CourseMembershipRepository memberships, CourseDocumentRepository documents,
            ChapterRepository chapters, UserRepository users) {
        this.courses = courses;
        this.semesters = semesters;
        this.memberships = memberships;
        this.documents = documents;
        this.chapters = chapters;
        this.users = users;
    }

    public List<CourseDto.CourseResponse> getMyCourses(UUID userId) {
        return courses.findByIsActiveTrueAndStatusNotOrderByCreatedAtDesc("ARCHIVED").stream()
                .filter(course -> semesters.findById(course.getSemesterWorkspaceId())
                        .map(s -> "ACTIVE".equals(s.getStatus())).orElse(false))
                .filter(course -> documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                        course.getCourseId(), "PROCESSED", "INDEXED"))
                .map(CourseDto.CourseResponse::fromEntity).toList();
    }

    public void requireCourseAccess(UUID courseId, UUID userId, boolean admin) {
        Course course = getCourse(courseId);
        boolean visible = Boolean.TRUE.equals(course.getIsActive())
                && !"ARCHIVED".equals(course.getStatus())
                && semesters.findById(course.getSemesterWorkspaceId()).map(s -> "ACTIVE".equals(s.getStatus())).orElse(false)
                && documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
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
            if (!documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                    id, "PROCESSED", "INDEXED")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Course needs at least one processed document before activation.");
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
        if (!Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status)) throw bad("Invalid course status.");
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
    public void archiveCourse(UUID id) {
        Course course = getCourse(id);
        course.setStatus("ARCHIVED");
        course.setIsActive(false);
        course.setUpdatedAt(LocalDateTime.now());
        courses.save(course);
    }

    public CourseDto.PublishChecklistResponse publishChecklist(UUID courseId) {
        Course course = getCourse(courseId);
        CourseDto.PublishChecklistResponse response = new CourseDto.PublishChecklistResponse();
        response.semesterActive = semesters.findById(course.getSemesterWorkspaceId())
                .map(s -> "ACTIVE".equals(s.getStatus())).orElse(false);
        response.processedDocument = documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
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
        return courses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
