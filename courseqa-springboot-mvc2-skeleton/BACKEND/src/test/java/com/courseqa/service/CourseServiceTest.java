package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.CourseDto;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.UserRole;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CourseServiceTest {
    @Test
    void newCourseStartsInactiveAndCreatesKnowledgeBase() {
        CourseRepository courses = mock(CourseRepository.class);
        CourseWorkspaceRepository workspaces = mock(CourseWorkspaceRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        SemesterWorkspaceRepository semesters = mock(SemesterWorkspaceRepository.class);
        CourseService service = new CourseService(
                courses,
                mock(ChapterRepository.class),
                workspaces,
                users,
                roles,
                semesters);
        UUID adminId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UserRole adminRole = mock(UserRole.class);
        when(adminRole.getRoleName()).thenReturn("ADMIN");
        when(users.existsById(adminId)).thenReturn(true);
        when(roles.findByUserIdAndIsActiveTrue(adminId)).thenReturn(List.of(adminRole));
        when(semesters.existsById(semesterId)).thenReturn(true);
        when(courses.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CourseDto.CreateCourseRequest request = new CourseDto.CreateCourseRequest();
        request.courseCode = "SWP391";
        request.courseName = "Software Project";
        request.semesterWorkspaceId = semesterId;

        service.createCourse(adminId, request);

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courses).save(courseCaptor.capture());
        assertFalse(courseCaptor.getValue().getIsActive());
        assertEquals("DRAFT", courseCaptor.getValue().getStatus());
        verify(workspaces).save(any());
    }
}
