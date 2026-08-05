package com.courseqa.service;

import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalWorkspaceService {
    private final CourseWorkspaceRepository workspaces;
    private final UserRepository users;
    private final SubscriptionService subscriptions;

    public PersonalWorkspaceService(
            CourseWorkspaceRepository workspaces,
            UserRepository users,
            SubscriptionService subscriptions
    ) {
        this.workspaces = workspaces;
        this.users = users;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public CourseWorkspace getOrCreate(UUID userId) {
        if (userId == null || !users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found.");
        }
        return workspaces
                .findFirstByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrueOrderByCreatedAtDesc(userId, "PRIVATE")
                .orElseGet(() -> create(userId, "Tài liệu cá nhân", "Kho tài liệu riêng của người dùng."));
    }

    public List<CourseWorkspace> list(UUID userId) {
        requireUser(userId);
        return workspaces.findByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrueOrderByCreatedAtDesc(
                userId, "PRIVATE");
    }

    @Transactional
    public CourseWorkspace create(UUID userId, String title, String description) {
        requireUser(userId);
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceTitle is required.");
        }
        com.courseqa.model.entity.SubscriptionPlan plan = subscriptions.effectivePlanForQuota(userId);
        long current = workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(userId, "PRIVATE");
        if (current >= plan.getMaxPersonalWorkspaces()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your plan supports at most " + plan.getMaxPersonalWorkspaces() + " Personal Workspace.");
        }
        LocalDateTime now = LocalDateTime.now();
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setCourseId(null);
        workspace.setOwnerUserId(userId);
        workspace.setWorkspaceTitle(title.trim());
        workspace.setDescription(description == null || description.isBlank() ? null : description.trim());
        workspace.setVisibility("PRIVATE");
        workspace.setIsActive(true);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        return workspaces.save(workspace);
    }

    private void requireUser(UUID userId) {
        if (userId == null || !users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found.");
        }
    }
}
