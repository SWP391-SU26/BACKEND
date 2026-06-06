package com.courseqa.repository;

import com.courseqa.model.entity.Course;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByIsActiveTrueOrderByCreatedAtDesc();
}
