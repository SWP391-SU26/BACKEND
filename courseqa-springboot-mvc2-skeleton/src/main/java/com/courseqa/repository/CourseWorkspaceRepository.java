package com.courseqa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courseqa.model.entity.CourseWorkspace;

// TODO: Extend JpaRepository after completing entity class.
// Example:
// public interface CourseWorkspaceRepository extends JpaRepository<CourseWorkspace, UUID> {}

public interface CourseWorkspaceRepository extends JpaRepository<CourseWorkspace, UUID> {
List<CourseWorkspace> findByCourseId(UUID courseId);
List<CourseWorkspace> findByOwnerUserId(UUID userId);
boolean existsByCourseIdAndWorkspaceTitle(UUID courseId, String workspaceTitle);
}
