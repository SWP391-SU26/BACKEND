package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PersonalWorkspaceServiceTest {

    private final CourseWorkspaceRepository workspaces = mock(CourseWorkspaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);

    private PersonalWorkspaceService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PersonalWorkspaceService(workspaces, users, subscriptions, documents);
        when(users.existsById(userId)).thenReturn(true);
        when(workspaces.save(any(CourseWorkspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ----------------------------------------------------------------- usage

    @Test
    void storageUsageMatchesWhatUploadValidationEnforces() {
        SubscriptionPlan plan = plan(10L * 1024 * 1024, 10, 100L * 1024 * 1024, 5);
        when(subscriptions.effectivePlanForQuota(userId)).thenReturn(plan);
        when(documents.sumFileSizeByUploadedByAndDocumentScope(userId, "PERSONAL")).thenReturn(42L);
        when(documents.countByUploadedByAndDocumentScope(userId, "PERSONAL")).thenReturn(3L);
        when(workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(userId, "PRIVATE"))
                .thenReturn(1L);

        PaymentDto.StorageUsageResponse usage = service.storageUsage(userId);

        assertEquals(42L, usage.usedBytes);
        assertEquals(100L * 1024 * 1024, usage.maxStorageBytes);
        assertEquals(3, usage.documentCount);
        assertEquals(10, usage.maxDocuments);
        assertEquals(1, usage.workspaceCount);
        assertEquals(5, usage.maxPersonalWorkspaces);
    }

    @Test
    void storageUsageReportsZeroInsteadOfNullWhenNothingUploadedYet() {
        when(subscriptions.effectivePlanForQuota(userId)).thenReturn(plan(1L, 1, 1L, 1));
        when(documents.sumFileSizeByUploadedByAndDocumentScope(userId, "PERSONAL")).thenReturn(null);
        when(documents.countByUploadedByAndDocumentScope(userId, "PERSONAL")).thenReturn(0L);

        assertEquals(0L, service.storageUsage(userId).usedBytes);
    }

    @Test
    void freePlanCanCreateItsFifthPersonalWorkspace() {
        when(subscriptions.effectivePlanForQuota(userId)).thenReturn(plan(10L, 10, 100L, 5));
        when(workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(userId, "PRIVATE"))
                .thenReturn(4L);

        CourseWorkspace created = service.create(userId, "Triết học", "Tài liệu theo chủ đề");

        assertEquals("Triết học", created.getWorkspaceTitle());
        verify(workspaces).save(any(CourseWorkspace.class));
    }

    @Test
    void freePlanRefusesAWorkspaceBeyondFive() {
        when(subscriptions.effectivePlanForQuota(userId)).thenReturn(plan(10L, 10, 100L, 5));
        when(workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(userId, "PRIVATE"))
                .thenReturn(5L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(userId, "Workspace thứ sáu", null));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(workspaces, never()).save(any());
    }

    // ---------------------------------------------------------------- update

    @Test
    void renamesAnOwnedWorkspace() {
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(ownedWorkspace()));

        CourseWorkspace updated = service.update(userId, workspaceId, "Kỳ 2", null);

        assertEquals("Kỳ 2", updated.getWorkspaceTitle());
    }

    @Test
    void refusesToRenameSomeoneElsesWorkspace() {
        CourseWorkspace workspace = ownedWorkspace();
        workspace.setOwnerUserId(UUID.randomUUID());
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(userId, workspaceId, "Hijacked", null));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void refusesToTouchACourseWorkspaceThroughThePersonalApi() {
        CourseWorkspace workspace = ownedWorkspace();
        workspace.setCourseId(UUID.randomUUID());
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(userId, workspaceId, "Not personal", null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    // ---------------------------------------------------------------- delete

    @Test
    void deletingAWorkspaceWithDocumentsIsRefused() {
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(ownedWorkspace()));
        when(documents.countByWorkspaceId(workspaceId)).thenReturn(2L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.delete(userId, workspaceId));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(workspaces, never()).save(any());
    }

    @Test
    void deletingTheOnlyWorkspaceIsRefused() {
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(ownedWorkspace()));
        when(documents.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(userId, "PRIVATE"))
                .thenReturn(1L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.delete(userId, workspaceId));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("You must keep at least one Personal Workspace.", error.getReason());
    }

    @Test
    void deletesAnEmptyNonDefaultWorkspace() {
        CourseWorkspace workspace = ownedWorkspace();
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(documents.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(workspaces.countByOwnerUserIdAndCourseIdIsNullAndVisibilityAndIsActiveTrue(userId, "PRIVATE"))
                .thenReturn(2L);

        service.delete(userId, workspaceId);

        assertEquals(false, workspace.getIsActive());
        verify(workspaces).save(workspace);
    }

    // ------------------------------------------------------------- fixtures

    private CourseWorkspace ownedWorkspace() {
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setWorkspaceId(workspaceId);
        workspace.setOwnerUserId(userId);
        workspace.setCourseId(null);
        workspace.setVisibility("PRIVATE");
        workspace.setIsActive(true);
        workspace.setWorkspaceTitle("Tài liệu cá nhân");
        return workspace;
    }

    private SubscriptionPlan plan(long maxFileBytes, int maxDocuments, long maxStorageBytes, int maxWorkspaces) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setMaxFileBytes(maxFileBytes);
        plan.setMaxDocuments(maxDocuments);
        plan.setMaxStorageBytes(maxStorageBytes);
        plan.setMaxPersonalWorkspaces(maxWorkspaces);
        return plan;
    }
}
