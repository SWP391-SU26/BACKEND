package com.courseqa.service;

import com.courseqa.model.dto.CourseDto;
import com.courseqa.model.entity.Chapter;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {

    private final UserRoleRepository userRoleRepository;
    private final SemesterWorkspaceRepository semesterWorkspaceRepository;

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;
    private final UserRepository userRepository;

    public CourseService(
        CourseRepository courseRepository,
        ChapterRepository chapterRepository,
        CourseWorkspaceRepository courseWorkspaceRepository,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository, SemesterWorkspaceRepository semesterWorkspaceRepository
) {
    this.courseRepository = courseRepository;
    this.chapterRepository = chapterRepository;
    this.courseWorkspaceRepository = courseWorkspaceRepository;
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.semesterWorkspaceRepository = semesterWorkspaceRepository;
}


private void requireTeacherOrAdmin(UUID requesterId) {
    if (requesterId == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }
    if (!userRepository.existsById(requesterId)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester user not found.");
    }
    boolean allowed = userRoleRepository.findByUserIdAndIsActiveTrue(requesterId).stream()
            .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getRoleName())
                        || "TEACHER".equalsIgnoreCase(r.getRoleName()));
    if (!allowed) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Teacher or Admin can perform this action.");
    }
}



    public List<CourseDto.CourseResponse> getCourses() {
        return courseRepository.findByStatusNotOrderByCreatedAtDesc("ARCHIVED").stream()
                .map(CourseDto.CourseResponse::fromEntity)
                .toList();
    }

    public CourseDto.CourseResponse createCourse(UUID requesterId, CourseDto.CreateCourseRequest request) {
        //added new !!!
        requireTeacherOrAdmin(requesterId);


        if (request == null || isBlank(request.courseCode) || isBlank(request.courseName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseCode and courseName are required.");
        }
        if (request.semesterWorkspaceId == null || !semesterWorkspaceRepository.existsById(request.semesterWorkspaceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semesterWorkspaceId is required and must exist.");
        }
       // I added this . ps: Khang
       if (courseRepository.existsByCourseCodeAndSemesterWorkspaceId(request.courseCode.trim(), request.semesterWorkspaceId)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Course code already exists in this semester.");
    }


        if (request.createdBy != null && !userRepository.existsById(request.createdBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "createdBy user not found.");
        }

        LocalDateTime now = LocalDateTime.now();
        Course course = new Course();
        course.setCourseCode(request.courseCode.trim());
        course.setCourseName(request.courseName.trim());
        course.setDescription(trimToNull(request.description));
        course.setCreatedBy(requesterId);
        course.setSemesterWorkspaceId(request.semesterWorkspaceId);
        course.setStatus("DRAFT");
        course.setIsActive(false);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);

        Course saved = courseRepository.save(course);
        CourseWorkspace knowledgeBase = new CourseWorkspace();
        knowledgeBase.setCourseId(saved.getCourseId());
        knowledgeBase.setOwnerUserId(requesterId);
        knowledgeBase.setWorkspaceTitle(saved.getCourseName() + " Knowledge Base");
        knowledgeBase.setDescription("System-managed knowledge base for " + saved.getCourseCode());
        knowledgeBase.setVisibility("COURSE");
        knowledgeBase.setIsActive(true);
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        courseWorkspaceRepository.save(knowledgeBase);
        return CourseDto.CourseResponse.fromEntity(saved);
    }

    public List<CourseDto.ChapterResponse> getChapters(UUID courseId) {
        ensureCourseExists(courseId);
        return chapterRepository.findByCourseIdAndIsActiveTrueOrderByOrderIndexAsc(courseId).stream()
                .map(CourseDto.ChapterResponse::fromEntity)
                .toList();
    }

    public CourseDto.ChapterResponse createChapter(UUID requesterId, UUID courseId, CourseDto.CreateChapterRequest request) {
        requireTeacherOrAdmin(requesterId);
        ensureCourseExists(courseId);
        if (request == null || isBlank(request.chapterTitle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chapterTitle is required.");
        }

        LocalDateTime now = LocalDateTime.now();
        Chapter chapter = new Chapter();
        chapter.setCourseId(courseId);
        chapter.setChapterTitle(request.chapterTitle.trim());
        chapter.setDescription(trimToNull(request.description));
        chapter.setOrderIndex(request.orderIndex == null || request.orderIndex < 1
                ? nextChapterOrder(courseId)
                : request.orderIndex);
        chapter.setIsActive(true);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);

        return CourseDto.ChapterResponse.fromEntity(chapterRepository.save(chapter));
    }

    public List<CourseDto.WorkspaceResponse> getAllWorkspaces() {
        return courseWorkspaceRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(CourseDto.WorkspaceResponse::fromEntity)
                .toList();
    }

    public List<CourseDto.WorkspaceResponse> getWorkspaces(UUID courseId) {
        ensureCourseExists(courseId);
        return courseWorkspaceRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(CourseDto.WorkspaceResponse::fromEntity)
                .toList();
    }

    public CourseDto.WorkspaceResponse createWorkspace(UUID requesterId, UUID courseId, CourseDto.CreateWorkspaceRequest request) {

        requireTeacherOrAdmin(requesterId);

        ensureCourseExists(courseId);
        if (request == null || isBlank(request.workspaceTitle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceTitle is required.");
        }
        if (request.ownerUserId != null && !userRepository.existsById(request.ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerUserId user not found.");
        }

        LocalDateTime now = LocalDateTime.now();
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setCourseId(courseId);
        workspace.setOwnerUserId(request.ownerUserId);
        workspace.setWorkspaceTitle(request.workspaceTitle.trim());
        workspace.setDescription(trimToNull(request.description));
        workspace.setVisibility(isBlank(request.visibility) ? "COURSE" : request.visibility.trim().toUpperCase());
        workspace.setIsActive(true);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);

        return CourseDto.WorkspaceResponse.fromEntity(courseWorkspaceRepository.save(workspace));
    }

    private void ensureCourseExists(UUID courseId) {
        if (courseId == null || !courseRepository.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
        }
    }

    private int nextChapterOrder(UUID courseId) {
        return chapterRepository.findByCourseIdOrderByOrderIndexAsc(courseId).stream()
                .map(Chapter::getOrderIndex)
                .filter(index -> index != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
