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
import com.courseqa.model.entity.User;
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
import java.time.LocalDateTime;
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
                mock(PersonalWorkspaceService.class),
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
    void documentListIncludesUploadDateAndUploaderNameWithOneBatchLookup() {
        UUID ownerId = UUID.randomUUID();
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 7, 27, 10, 30);
        CourseDocument document = new CourseDocument();
        document.setDocumentId(UUID.randomUUID());
        document.setUploadedBy(ownerId);
        document.setUploadedAt(uploadedAt);
        document.setDocumentScope("PERSONAL");
        document.setReviewStatus("NOT_SUBMITTED");

        User uploader = new User();
        uploader.setUserId(ownerId);
        uploader.setFullName("Nguyen Van A");

        when(users.existsById(ownerId)).thenReturn(true);
        when(documents.findByUploadedByOrderByUploadedAtDesc(ownerId)).thenReturn(List.of(document));
        when(users.findAllById(any())).thenReturn(List.of(uploader));

        List<DocumentDto.DocumentResponse> response = service.getMyDocuments(ownerId);

        assertEquals(1, response.size());
        assertEquals(uploadedAt, response.get(0).uploadedAt);
        assertEquals("Nguyen Van A", response.get(0).uploaderName);
        verify(users).findAllById(any());
    }
}
