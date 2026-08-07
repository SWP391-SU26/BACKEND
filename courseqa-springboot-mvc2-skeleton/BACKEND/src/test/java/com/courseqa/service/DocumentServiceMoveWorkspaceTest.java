package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.DocumentDto;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.repository.ChapterRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.DocumentChapterRangeRepository;
import com.courseqa.repository.DocumentChapterSuggestionRepository;
import com.courseqa.repository.DocumentChunkRepository;
import com.courseqa.repository.DocumentPageRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.UserRepository;
import com.courseqa.repository.UserRoleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * REQ-02 WS-US-03: moving a personal document between the owner's workspaces.
 * Separate file from DocumentServicePersonalTest to keep PersonalWorkspaceService
 * as a real collaborator here (its requireOwnedWorkspace guard is what this
 * feature actually depends on), instead of the mocked stand-in used elsewhere.
 */
class DocumentServiceMoveWorkspaceTest {

    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
    private final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    private final CourseWorkspaceRepository workspaceRepo = mock(CourseWorkspaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);

    private DocumentService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID sourceWorkspaceId = UUID.randomUUID();
    private final UUID targetWorkspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PersonalWorkspaceService personalWorkspaces = new PersonalWorkspaceService(
                workspaceRepo, users, subscriptions, documents);

        service = new DocumentService(
                documents,
                mock(CourseRepository.class),
                mock(ChapterRepository.class),
                workspaceRepo,
                mock(DocumentPageRepository.class),
                chunks,
                users,
                mock(UserRoleRepository.class),
                mock(SemesterWorkspaceRepository.class),
                mock(DocumentChapterRangeRepository.class),
                mock(DocumentChapterSuggestionRepository.class),
                mock(JdbcTemplate.class),
                personalWorkspaces,
                subscriptions,
                "uploads", "", "", "", "",
                "vie+eng", 60,
                new ChunkTokenCounter("", false), disabledSemantics(), 450, 55, 250, 40);

        when(users.existsById(userId)).thenReturn(true);
        when(documents.save(any(CourseDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void movesAPersonalDocumentAndItsChunksToTheTargetWorkspace() {
        when(documents.findById(documentId)).thenReturn(Optional.of(personalDocument()));
        when(workspaceRepo.findById(targetWorkspaceId)).thenReturn(Optional.of(personalWorkspace(targetWorkspaceId)));

        DocumentDto.DocumentResponse response = service.moveToWorkspace(documentId, userId, targetWorkspaceId);

        assertEquals(targetWorkspaceId, response.workspaceId);
        verify(chunks).updateWorkspaceIdByDocumentId(documentId, targetWorkspaceId);
    }

    @Test
    void movingToTheSameWorkspaceIsANoOpThatSkipsTheChunkUpdate() {
        when(documents.findById(documentId)).thenReturn(Optional.of(personalDocument()));
        when(workspaceRepo.findById(sourceWorkspaceId)).thenReturn(Optional.of(personalWorkspace(sourceWorkspaceId)));

        service.moveToWorkspace(documentId, userId, sourceWorkspaceId);

        verify(chunks, never()).updateWorkspaceIdByDocumentId(any(), any());
        verify(documents, never()).save(any());
    }

    @Test
    void refusesToMoveIntoAWorkspaceOwnedBySomeoneElse() {
        when(documents.findById(documentId)).thenReturn(Optional.of(personalDocument()));
        CourseWorkspace someoneElses = personalWorkspace(targetWorkspaceId);
        someoneElses.setOwnerUserId(UUID.randomUUID());
        when(workspaceRepo.findById(targetWorkspaceId)).thenReturn(Optional.of(someoneElses));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.moveToWorkspace(documentId, userId, targetWorkspaceId));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(chunks, never()).updateWorkspaceIdByDocumentId(any(), any());
    }

    @Test
    void refusesToMoveADocumentAlreadySharedWithACourse() {
        CourseDocument document = personalDocument();
        document.setDocumentScope("COURSE");
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.moveToWorkspace(documentId, userId, targetWorkspaceId));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void refusesToMoveSomeoneElsesDocument() {
        CourseDocument document = personalDocument();
        document.setUploadedBy(UUID.randomUUID());
        when(documents.findById(documentId)).thenReturn(Optional.of(document));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.moveToWorkspace(documentId, userId, targetWorkspaceId));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    // ------------------------------------------------------------- fixtures

    private CourseDocument personalDocument() {
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setUploadedBy(userId);
        document.setDocumentScope("PERSONAL");
        document.setWorkspaceId(sourceWorkspaceId);
        document.setReviewStatus("NOT_SUBMITTED");
        return document;
    }

    private CourseWorkspace personalWorkspace(UUID id) {
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setWorkspaceId(id);
        workspace.setOwnerUserId(userId);
        workspace.setCourseId(null);
        workspace.setVisibility("PRIVATE");
        workspace.setIsActive(true);
        return workspace;
    }

    private SemanticBoundaryDetector disabledSemantics() {
        return new SemanticBoundaryDetector(mock(EmbeddingService.class), false, 0.62, 4000);
    }
}
