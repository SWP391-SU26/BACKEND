package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.CourseDto;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.SemesterWorkspace;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseMembershipRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class Flow2ServiceTest {
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
    private final SemesterWorkspaceRepository semesters = mock(SemesterWorkspaceRepository.class);
    private Flow2Service service;

    @BeforeEach
    void setUp() {
        service = new Flow2Service(
                courses,
                semesters,
                mock(CourseMembershipRepository.class),
                documents,
                mock(ChapterRepository.class),
                mock(UserRepository.class));
    }

    @Test
    void activationWithoutProcessedDocumentReturnsConflict() {
        UUID courseId = UUID.randomUUID();
        Course course = mock(Course.class);
        when(course.getStatus()).thenReturn("DRAFT");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        CourseDto.UpdateCourseRequest request = new CourseDto.UpdateCourseRequest();
        request.isActive = true;

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateCourse(courseId, request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(course, never()).setIsActive(true);
        verify(courses, never()).save(course);
    }

    @Test
    void activationWithProcessedDocumentSucceeds() {
        UUID courseId = UUID.randomUUID();
        Course course = mock(Course.class);
        when(course.getStatus()).thenReturn("DRAFT");
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                courseId, "PROCESSED", "INDEXED")).thenReturn(true);
        when(courses.save(course)).thenReturn(course);
        CourseDto.UpdateCourseRequest request = new CourseDto.UpdateCourseRequest();
        request.isActive = true;

        service.updateCourse(courseId, request);

        verify(course).setIsActive(true);
        verify(courses).save(course);
    }

    @Test
    void myCoursesDoesNotRequireMembershipOrPublishedStatus() {
        UUID courseId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        Course course = mock(Course.class);
        SemesterWorkspace semester = mock(SemesterWorkspace.class);
        when(course.getCourseId()).thenReturn(courseId);
        when(course.getSemesterWorkspaceId()).thenReturn(semesterId);
        when(course.getStatus()).thenReturn("DRAFT");
        when(course.getIsActive()).thenReturn(true);
        when(semester.getStatus()).thenReturn("ACTIVE");
        when(courses.findByIsActiveTrueAndStatusNotOrderByCreatedAtDesc("ARCHIVED"))
                .thenReturn(List.of(course));
        when(semesters.findById(semesterId)).thenReturn(Optional.of(semester));
        when(documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                courseId, "PROCESSED", "INDEXED")).thenReturn(true);

        List<CourseDto.CourseResponse> result = service.getMyCourses(UUID.randomUUID());

        assertEquals(1, result.size());
        assertEquals(courseId, result.get(0).courseId);
    }
}
