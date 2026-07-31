package com.courseqa.repository;

import com.courseqa.model.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByWorkspaceIdAndIsActiveTrue(UUID workspaceId);

    Optional<ChatSession> findByUserIdAndWorkspaceIdAndIsActiveTrue(UUID userId, UUID workspaceId);
    List<ChatSession> findByUserIdAndCourseIdAndIsActiveTrueOrderByUpdatedAtDesc(UUID userId, UUID courseId);
    List<ChatSession> findByUserIdAndSemesterWorkspaceIdAndIsActiveTrueOrderByUpdatedAtDesc(UUID userId, UUID semesterWorkspaceId);
    List<ChatSession> findByUserIdAndScopeTypeAndIsActiveTrueOrderByUpdatedAtDesc(UUID userId, String scopeType);
    List<ChatSession> findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(UUID userId);
}
