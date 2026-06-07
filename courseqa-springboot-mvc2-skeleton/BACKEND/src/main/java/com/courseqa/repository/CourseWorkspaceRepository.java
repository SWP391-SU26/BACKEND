package com.courseqa.repository;

import com.courseqa.model.entity.CourseWorkspace;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseWorkspaceRepository extends JpaRepository<CourseWorkspace, UUID> {
    List<CourseWorkspace> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    List<CourseWorkspace> findByIsActiveTrueOrderByCreatedAtDesc();
}
