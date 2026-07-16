package com.courseqa.service;

import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalWorkspaceService {
    private final CourseWorkspaceRepository workspaces;
    private final UserRepository users;

    public PersonalWorkspaceService(CourseWorkspaceRepository workspaces, UserRepository users) {
        this.workspaces = workspaces;
        this.users = users;
    }

    @Transactional
    public CourseWorkspace getOrCreate(UUID userId) {
        if (userId == null || !users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found.");
        }
        return workspaces
                .findFirstByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrueOrderByCreatedAtDesc(userId, "PRIVATE")
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    CourseWorkspace workspace = new CourseWorkspace();
                    workspace.setCourseId(null);
                    workspace.setOwnerUserId(userId);
                    workspace.setWorkspaceTitle("Tài liệu cá nhân");
                    workspace.setDescription("Kho tài liệu riêng của người dùng.");
                    workspace.setVisibility("PRIVATE");
                    workspace.setIsActive(true);
                    workspace.setCreatedAt(now);
                    workspace.setUpdatedAt(now);
                    return workspaces.save(workspace);
                });
    }
}
