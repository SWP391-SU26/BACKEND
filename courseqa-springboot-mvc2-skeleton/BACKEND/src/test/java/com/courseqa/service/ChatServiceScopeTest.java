package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionDocumentRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.CourseDocumentRepository;
import com.courseqa.repository.CourseMembershipRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ChatServiceScopeTest {
    private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    private final ChatSessionDocumentRepository sessionDocuments = mock(ChatSessionDocumentRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final CourseWorkspaceRepository workspaces = mock(CourseWorkspaceRepository.class);
    private final AIClientService ai = mock(AIClientService.class);
    private final AnswerCitationRepository citations = mock(AnswerCitationRepository.class);
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final SemesterWorkspaceRepository semesters = mock(SemesterWorkspaceRepository.class);
    private final CourseDocumentRepository documents = mock(CourseDocumentRepository.class);
    private final LearningScopeService learningScope = mock(LearningScopeService.class);
    private final PersonalWorkspaceService personalWorkspaces = mock(PersonalWorkspaceService.class);
    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(sessions, sessionDocuments, messages, workspaces, ai, citations, retrieval,
                mock(CourseMembershipRepository.class), roles, courses, semesters, documents, learningScope,
                personalWorkspaces, new QuestionScopeGuard());
        when(sessions.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getChatSessionId() == null) session.setChatSessionId(UUID.randomUUID());
            return session;
        });
    }

    @Test
    void documentScopeStoresOnlyProcessedDocumentsFromOneCourse() {
        UUID userId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Course course = course(courseId, semesterId);
        CourseWorkspace workspace = workspace(workspaceId, courseId);
        CourseDocument first = document(UUID.randomUUID(), courseId, "PROCESSED");
        CourseDocument second = document(UUID.randomUUID(), courseId, "PROCESSED");
        when(learningScope.requireAccessibleCourse(courseId, userId, false)).thenReturn(course);
        when(learningScope.requireActiveWorkspace(courseId)).thenReturn(workspace);
        when(documents.findAllById(List.of(first.getDocumentId(), second.getDocumentId())))
                .thenReturn(List.of(first, second));

        ChatSession created = service.createSession(userId, "DOCUMENTS", semesterId, courseId,
                List.of(first.getDocumentId(), second.getDocumentId()), false, null);

        assertEquals("DOCUMENTS", created.getScopeType());
        assertEquals(semesterId, created.getSemesterWorkspaceId());
        assertEquals(workspaceId, created.getWorkspaceId());
        verify(sessionDocuments, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void documentScopeRejectsDocumentFromAnotherCourse() {
        UUID userId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseDocument foreign = document(UUID.randomUUID(), UUID.randomUUID(), "PROCESSED");
        when(learningScope.requireAccessibleCourse(courseId, userId, false)).thenReturn(course(courseId, semesterId));
        when(learningScope.requireActiveWorkspace(courseId)).thenReturn(workspace(UUID.randomUUID(), courseId));
        when(documents.findAllById(List.of(foreign.getDocumentId()))).thenReturn(List.of(foreign));

        assertThrows(ResponseStatusException.class, () -> service.createSession(userId, "DOCUMENTS",
                semesterId, courseId, List.of(foreign.getDocumentId()), false, null));
        verify(sessions, never()).save(any());
    }

    @Test
    void semesterScopeDoesNotInventCourseOrWorkspace() {
        UUID userId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        when(learningScope.accessibleCoursesInSemester(semesterId, userId, false))
                .thenReturn(List.of(course(UUID.randomUUID(), semesterId)));

        ChatSession created = service.createSession(userId, "SEMESTER", semesterId, null, List.of(), false, null);

        assertEquals("SEMESTER", created.getScopeType());
        assertNull(created.getCourseId());
        assertNull(created.getWorkspaceId());
    }

    @Test
    void personalScopeStoresOnlyDocumentsOwnedByCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        CourseDocument document = document(UUID.randomUUID(), null, "PROCESSED");
        document.setUploadedBy(userId);
        CourseWorkspace workspace = workspace(workspaceId, null);
        when(documents.findAllById(List.of(document.getDocumentId()))).thenReturn(List.of(document));
        when(personalWorkspaces.getOrCreate(userId)).thenReturn(workspace);

        ChatSession created = service.createSession(userId, "PERSONAL", null, null,
                List.of(document.getDocumentId()), false, null);

        assertEquals("PERSONAL", created.getScopeType());
        assertEquals(workspaceId, created.getWorkspaceId());
        assertNull(created.getCourseId());
        verify(sessionDocuments).save(any());
    }

    @Test
    void personalScopeRejectsAnotherUsersDocument() {
        UUID userId = UUID.randomUUID();
        CourseDocument document = document(UUID.randomUUID(), null, "PROCESSED");
        document.setUploadedBy(UUID.randomUUID());
        when(documents.findAllById(List.of(document.getDocumentId()))).thenReturn(List.of(document));

        assertThrows(ResponseStatusException.class, () -> service.createSession(userId, "PERSONAL",
                null, null, List.of(document.getDocumentId()), false, null));
        verify(sessions, never()).save(any());
    }

    @Test
    void greetingReturnsWithoutCallingRetrievalOrPython() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setChatSessionId(sessionId);
        session.setUserId(userId);
        session.setCourseId(courseId);
        session.setSemesterWorkspaceId(semesterId);
        session.setScopeType("COURSE");
        session.setIsActive(true);
        session.setSessionTitle("New conversation");
        CourseDocument available = document(UUID.randomUUID(), courseId, "PROCESSED");
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(roles.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of());
        when(learningScope.requireAccessibleCourse(courseId, userId, false)).thenReturn(course(courseId, semesterId));
        when(learningScope.requireActiveWorkspace(courseId)).thenReturn(workspace(UUID.randomUUID(), courseId));
        when(documents.findByCourseIdAndProcessingStatusOrderByUploadedAtDesc(courseId, "PROCESSED"))
                .thenReturn(List.of(available));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setMessageId(UUID.randomUUID());
            return message;
        });

        ChatDto.AskResponse response = service.askQuestion(sessionId, "Xin chào");

        assertEquals("GREETING", response.generationMode);
        verify(retrieval, never()).retrieve(any());
        verify(ai, never()).callGenerate(any(), any());
    }

    private Course course(UUID courseId, UUID semesterId) {
        Course course = new Course();
        course.setCourseId(courseId);
        course.setSemesterWorkspaceId(semesterId);
        course.setCourseCode("TEST101");
        return course;
    }

    private CourseWorkspace workspace(UUID workspaceId, UUID courseId) {
        CourseWorkspace workspace = new CourseWorkspace();
        workspace.setWorkspaceId(workspaceId);
        workspace.setCourseId(courseId);
        workspace.setIsActive(true);
        return workspace;
    }

    private CourseDocument document(UUID documentId, UUID courseId, String status) {
        CourseDocument document = new CourseDocument();
        document.setDocumentId(documentId);
        document.setCourseId(courseId);
        document.setProcessingStatus(status);
        return document;
    }
}
