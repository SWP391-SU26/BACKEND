package com.courseqa.service;

import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalWorkspaceService {
    private static final String PERSONAL_SCOPE = "PERSONAL";

    private final CourseWorkspaceRepository workspaces;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final CourseDocumentRepository documents;

    public PersonalWorkspaceService(
            CourseWorkspaceRepository workspaces,
            UserRepository users,
            SubscriptionService subscriptions,
            CourseDocumentRepository documents
    ) {
        this.workspaces = workspaces;
        this.users = users;
        this.subscriptions = subscriptions;
        this.documents = documents;
    }

    /** REQ-02 WS-US-02: quota is account-wide, so this reports the same totals DocumentService enforces on upload. */
    @Transactional(readOnly = true)
    public PaymentDto.StorageUsageResponse storageUsage(UUID userId) {
        requireUser(userId);
        SubscriptionPlan plan = subscriptions.effectivePlanForQuota(userId);

        PaymentDto.StorageUsageResponse usage = new PaymentDto.StorageUsageResponse();
        usage.usedBytes = Optional.ofNullable(
                documents.sumFileSizeByUploadedByAndDocumentScope(userId, PERSONAL_SCOPE)).orElse(0L);
        usage.maxStorageBytes = plan.getMaxStorageBytes();
        usage.documentCount = (int) documents.countByUploadedByAndDocumentScope(userId, PERSONAL_SCOPE);
        usage.maxDocuments = plan.getMaxDocuments();
        usage.maxFileBytes = plan.getMaxFileBytes();
        usage.workspaceCount = (int) workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(
                userId, "PRIVATE");
        usage.maxPersonalWorkspaces = plan.getMaxPersonalWorkspaces();
        return usage;
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

    /** REQ-02 WS-FR-01..03: rename/redescribe an owned workspace. */
    @Transactional
    public CourseWorkspace update(UUID userId, UUID workspaceId, String title, String description) {
        CourseWorkspace workspace = requireOwnedWorkspace(userId, workspaceId);
        if (title != null) {
            if (title.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspaceTitle cannot be blank.");
            }
            workspace.setWorkspaceTitle(title.trim());
        }
        if (description != null) {
            workspace.setDescription(description.isBlank() ? null : description.trim());
        }
        workspace.setUpdatedAt(LocalDateTime.now());
        return workspaces.save(workspace);
    }

    /**
     * Soft-deletes an owned workspace. Refuses when it still holds documents (move
     * or delete them first - losing a student's files silently is worse than an
     * extra click) or when it is the account's only workspace (there must always
     * be somewhere for personal uploads to land).
     */
    @Transactional
    public void delete(UUID userId, UUID workspaceId) {
        CourseWorkspace workspace = requireOwnedWorkspace(userId, workspaceId);
        if (documents.countByWorkspaceId(workspaceId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Move or remove the documents in this workspace before deleting it.");
        }
        long remaining = workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(
                userId, "PRIVATE");
        if (remaining <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You must keep at least one Personal Workspace.");
        }
        workspace.setIsActive(false);
        workspace.setUpdatedAt(LocalDateTime.now());
        workspaces.save(workspace);
    }

    /** Shared by move-document: confirms a workspace exists, is a personal one, and belongs to this user. */
    CourseWorkspace requireOwnedWorkspace(UUID userId, UUID workspaceId) {
        requireUser(userId);
        CourseWorkspace workspace = workspaces.findById(workspaceId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        if (workspace.getCourseId() != null || !"PRIVATE".equals(workspace.getVisibility())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a personal workspace.");
        }
        if (!userId.equals(workspace.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This workspace belongs to another user.");
        }
        return workspace;
    }

    private void requireUser(UUID userId) {
        if (userId == null || !users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found.");
        }
    }
}
