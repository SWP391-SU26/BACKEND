package com.courseqa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courseqa.model.entity.Course;

// TODO: Extend JpaRepository after completing entity class.
// Example:
// public interface CourseRepository extends JpaRepository<Course, UUID> {}

public interface CourseRepository extends JpaRepository<Course, UUID> {
List<Course> findByIsActiveTrue();
List<Course> findByCreatedBy(UUID createdBy);
boolean existsByCourseCode(String courseCode);
}
