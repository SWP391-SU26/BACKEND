package com.courseqa.service;

import com.courseqa.model.dto.CourseDto;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;

    public CourseService(CourseRepository courseRepository, CourseWorkspaceRepository courseWorkspaceRepository) {
        this.courseRepository = courseRepository;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
    }

    public List<CourseDto.CourseResponse> getCourses() {
        return courseRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(CourseDto.CourseResponse::fromEntity)
                .toList();
    }

    public List<CourseDto.WorkspaceResponse> getAllWorkspaces() {
        return courseWorkspaceRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(CourseDto.WorkspaceResponse::fromEntity)
                .toList();
    }

    public List<CourseDto.WorkspaceResponse> getWorkspaces(UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found.");
        }
        return courseWorkspaceRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(CourseDto.WorkspaceResponse::fromEntity)
                .toList();
    }
}
