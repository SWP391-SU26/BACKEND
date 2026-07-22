package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.DocumentDto;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.DocumentChunk;
import com.courseqa.model.entity.SemesterWorkspace;
import com.courseqa.model.entity.UserRole;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DocumentServicePersonalTest {
    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseWorkspaceRepository workspaces = mock(CourseWorkspaceRepository.class);
    private final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final SemesterWorkspaceRepository semesters = mock(SemesterWorkspaceRepository.class);
    private final PersonalWorkspaceService personalWorkspaces = mock(PersonalWorkspaceService.class);
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                documents,
                courses,
                mock(ChapterRepository.class),
                workspaces,
                mock(DocumentPageRepository.class),
                chunks,
                users,
                roles,
                semesters,
                mock(DocumentChapterRangeRepository.class),
                mock(DocumentChapterSuggestionRepository.class),
                mock(JdbcTemplate.class),
                personalWorkspaces,
                mock(EmbeddingService.class),
                "uploads",
                "",
                "",
                "");
        when(documents.save(any(CourseDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submitAndApproveMovesTheSameDocumentAndChunksToCourseWorkspace() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID courseWorkspaceId = UUID.randomUUID();

        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setUploadedBy(ownerId);
        document.setDocumentScope("PERSONAL");
        document.setReviewStatus("NOT_SUBMITTED");
        document.setProcessingStatus("PROCESSED");
        document.setIndexingStatus("INDEXED");

        Course course = new Course();
        course.setCourseId(courseId);
        course.setSemesterWorkspaceId(semesterId);
        course.setStatus("DRAFT");
        course.setIsActive(true);
        SemesterWorkspace semester = new SemesterWorkspace();
        semester.setStatus("ACTIVE");
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setWorkspaceId(courseWorkspaceId);
        workspace.setCourseId(courseId);
        workspace.setIsActive(true);
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setDocumentId(documentId);

        when(users.existsById(ownerId)).thenReturn(true);
        when(users.existsById(adminId)).thenReturn(true);
        when(documents.findById(documentId)).thenReturn(Optional.of(document));
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(semesters.findById(semesterId)).thenReturn(Optional.of(semester));
        when(workspaces.findByCourseIdOrderByCreatedAtDesc(courseId)).thenReturn(List.of(workspace));
        when(chunks.findByDocumentIdOrderByChunkIndexAsc(documentId)).thenReturn(List.of(chunk));
        UserRole adminRole = new UserRole();
        adminRole.setRoleName("ADMIN");
        when(roles.findByUserIdAndIsActiveTrue(adminId)).thenReturn(List.of(adminRole));

        DocumentDto.DocumentResponse submitted = service.submitForReview(documentId, courseId, ownerId);
        assertEquals("PENDING", submitted.reviewStatus);

        DocumentDto.ReviewRequest review = new DocumentDto.ReviewRequest();
        review.status = "APPROVED";
        review.courseId = courseId;
        DocumentDto.DocumentResponse approved = service.reviewDocument(documentId, review, adminId);

        assertEquals(documentId, approved.documentId);
        assertEquals("COURSE", approved.documentScope);
        assertEquals("APPROVED", approved.reviewStatus);
        assertEquals(courseWorkspaceId, approved.workspaceId);
        assertSame(chunk, chunks.findByDocumentIdOrderByChunkIndexAsc(documentId).get(0));
        assertEquals(courseWorkspaceId, chunk.getWorkspaceId());
        assertEquals(courseId, chunk.getCourseId());
        verify(chunks).saveAll(List.of(chunk));
    }

    @Test
    void uploaderRestoresSharedDocumentAsUnsubmittedPersonalDocument() {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID courseWorkspaceId = UUID.randomUUID();
        UUID personalWorkspaceId = UUID.randomUUID();
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setUploadedBy(ownerId);
        document.setWorkspaceId(courseWorkspaceId);
        document.setCourseId(courseId);
        document.setDocumentScope("COURSE");
        document.setReviewStatus("APPROVED");
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(documentId);
        CourseWorkspace personal = new CourseWorkspace();
        personal.setWorkspaceId(personalWorkspaceId);
        personal.setOwnerUserId(ownerId);

        when(users.existsById(ownerId)).thenReturn(true);
        when(documents.findById(documentId)).thenReturn(Optional.of(document));
        when(chunks.findByDocumentIdOrderByChunkIndexAsc(documentId)).thenReturn(List.of(chunk));
        when(personalWorkspaces.getOrCreate(ownerId)).thenReturn(personal);

        service.deleteDocument(documentId, ownerId);
        DocumentDto.DocumentResponse restored = service.restoreDocument(documentId, ownerId);

        assertEquals("PERSONAL", restored.documentScope);
        assertEquals("NOT_SUBMITTED", restored.reviewStatus);
        assertEquals(personalWorkspaceId, restored.workspaceId);
        assertEquals(null, restored.courseId);
        assertEquals(personalWorkspaceId, chunk.getWorkspaceId());
        assertEquals(null, chunk.getCourseId());
    }
}
