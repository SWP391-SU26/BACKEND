package com.courseqa.repository;

import com.courseqa.model.entity.CourseWorkspace;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseWorkspaceRepository extends JpaRepository<CourseWorkspace, UUID> {
    List<CourseWorkspace> findByCourseIdOrderByCreatedAtDesc(UUID courseId);

    List<CourseWorkspace> findByIsActiveTrueOrderByCreatedAtDesc();

    long countByIsActiveTrue();

    Optional<CourseWorkspace> findFirstByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrueOrderByCreatedAtDesc(
            UUID ownerUserId, String visibility);

    List<CourseWorkspace> findByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrueOrderByCreatedAtDesc(
            UUID ownerUserId, String visibility);

    long countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(UUID ownerUserId, String visibility);
}
