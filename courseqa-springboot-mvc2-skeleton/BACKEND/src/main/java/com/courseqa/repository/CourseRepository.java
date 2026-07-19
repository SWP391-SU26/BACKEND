package com.courseqa.repository;

import com.courseqa.model.entity.Course;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByIsActiveTrueOrderByCreatedAtDesc();
    List<Course> findByStatusNotOrderByCreatedAtDesc(String status);
    List<Course> findByIsActiveTrueAndStatusNotOrderByCreatedAtDesc(String status);
    long countByIsActiveTrue();
    boolean existsByCourseCode(String courseCode);
    boolean existsByCourseCodeAndSemesterWorkspaceId(String courseCode, UUID semesterWorkspaceId);
    List<Course> findBySemesterWorkspaceIdOrderByCreatedAtDesc(UUID semesterWorkspaceId);
    List<Course> findByCourseIdInAndStatusAndIsActiveTrueOrderByCreatedAtDesc(List<UUID> courseIds, String status);
}
