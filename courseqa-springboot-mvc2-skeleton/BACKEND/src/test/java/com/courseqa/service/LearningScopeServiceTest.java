package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.SemesterWorkspace;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.DocumentChapterRangeRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LearningScopeServiceTest {
    private final SemesterWorkspaceRepository semesters = mock(SemesterWorkspaceRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
    private LearningScopeService service;

    @BeforeEach
    void setUp() {
        service = new LearningScopeService(
                semesters,
                courses,
                mock(CourseWorkspaceRepository.class),
                documents,
                mock(ChapterRepository.class),
                mock(DocumentChapterRangeRepository.class),
                mock(UserRepository.class));
    }

    @Test
    void inactiveSemesterBlocksEveryUser() {
        Fixture fixture = fixture("DRAFT", "DRAFT", true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireAccessibleCourse(fixture.courseId, UUID.randomUUID(), true));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void authenticatedUserCanOpenActiveCourseWithoutMembershipOrPublishedStatus() {
        Fixture fixture = fixture("DRAFT", "ACTIVE", true);

        Course accessible = service.requireAccessibleCourse(fixture.courseId, UUID.randomUUID(), false);

        assertSame(fixture.course, accessible);
    }

    @Test
    void inactiveCourseReceivesForbidden() {
        Fixture fixture = fixture("DRAFT", "ACTIVE", false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireAccessibleCourse(fixture.courseId, UUID.randomUUID(), false));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void archivedSemesterBlocksAdminPreview() {
        Fixture fixture = fixture("DRAFT", "ARCHIVED", true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireAccessibleCourse(fixture.courseId, UUID.randomUUID(), true));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void courseWithoutProcessedDocumentReceivesForbidden() {
        Fixture fixture = fixture("DRAFT", "ACTIVE", true);
        when(documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                fixture.courseId, "PROCESSED", "INDEXED"))
                .thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireAccessibleCourse(fixture.courseId, UUID.randomUUID(), false));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    private Fixture fixture(String courseStatus, String semesterStatus, boolean active) {
        UUID courseId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        Course course = mock(Course.class);
        SemesterWorkspace semester = mock(SemesterWorkspace.class);
        when(course.getSemesterWorkspaceId()).thenReturn(semesterId);
        when(course.getCourseId()).thenReturn(courseId);
        when(course.getStatus()).thenReturn(courseStatus);
        when(course.getIsActive()).thenReturn(active);
        when(semester.getStatus()).thenReturn(semesterStatus);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(semesters.findById(semesterId)).thenReturn(Optional.of(semester));
        when(documents.existsByCourseIdAndProcessingStatusAndIndexingStatus(
                courseId, "PROCESSED", "INDEXED")).thenReturn(true);
        return new Fixture(courseId, course);
    }

    private record Fixture(UUID courseId, Course course) {}
}
