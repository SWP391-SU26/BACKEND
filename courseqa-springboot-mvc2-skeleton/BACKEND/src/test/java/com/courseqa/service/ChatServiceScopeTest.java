package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.UserRole;
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
        when(documents.findByCourseIdAndProcessingStatusAndIndexingStatusOrderByUploadedAtDesc(
                courseId, "PROCESSED", "INDEXED"))
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

    @Test
    void localFallbackIsTransparentAndUsesMarkdownBullets() {
        RagDto.RetrievedChunk first = new RagDto.RetrievedChunk();
        first.content = "Vật chất tồn tại khách quan và có trước ý thức.";
        RagDto.RetrievedChunk second = new RagDto.RetrievedChunk();
        second.content = "Ý thức phản ánh thế giới vật chất thông qua bộ óc con người.";

        String answer = service.buildLocalFallbackAnswer(
                "Vật chất và ý thức có quan hệ thế nào?",
                List.of(first, second)
        );

        assertTrue(answer.startsWith("### Chưa thể tổng hợp câu trả lời"));
        assertTrue(answer.contains("- Vật chất tồn tại khách quan"));
        assertTrue(answer.contains("- Ý thức phản ánh thế giới vật chất"));
        assertTrue(answer.contains("không phải câu trả lời do AI tổng hợp"));
    }

    @Test
    void reasoningDisplayFormatterRecoversInlineNumberedOutput() {
        String raw = "Vật chất quyết định ý thức.\n"
                + "2. Vật chất là nguồn gốc của ý thức.\n"
                + "3. Ý thức tác động trở lại vật chất thông qua thực tiễn.";

        String answer = service.formatAnswerForDisplay(raw, "reasoning");

        assertTrue(answer.startsWith("**Trả lời trực tiếp:** Vật chất quyết định ý thức."));
        assertTrue(answer.contains("\n- Vật chất là nguồn gốc của ý thức."));
        assertTrue(answer.contains("\n- Ý thức tác động trở lại vật chất"));
        assertFalse(answer.contains("**Kết luận:** Vật chất quyết định ý thức."));
    }

    @Test
    void definitionFormatterKeepsClausesInOneCoherentParagraph() {
        String raw = "**Định nghĩa:** Triết học là hệ thống tri thức lý luận chung nhất "
                + "của con người về thế giới;\n\n"
                + "**Đặc điểm chính:**\n"
                + "- nghiên cứu những quy luật chung của tự nhiên, xã hội và tư duy.\n"
                + "- về vị trí và vai trò của con người trong thế giới ấy.";

        String answer = service.formatAnswerForDisplay(raw, "definition");

        assertTrue(answer.startsWith("**Định nghĩa:** Triết học là hệ thống"));
        assertFalse(answer.contains("**Đặc điểm chính:**"));
        assertFalse(answer.contains("\n- "));
        assertTrue(answer.contains("về vị trí và vai trò"));
    }

    @Test
    void definitionFormatterPreservesStructuredAttributedDefinition() {
        String markdown = "**Ph\u00e1t bi\u1ec3u:** \u201cA l\u00e0 B.\u201d\n\n"
                + "**C\u00e1c m\u1eb7t/ph\u1ea7n ch\u00ednh:**\n"
                + "1. **M\u1eb7t th\u1ee9 nh\u1ea5t:** N\u1ed9i dung m\u1ed9t.\n"
                + "2. **M\u1eb7t th\u1ee9 hai:** N\u1ed9i dung hai.";

        assertEquals(markdown, service.formatAnswerForDisplay(markdown, "definition"));
    }

    @Test
    void displayFormatterPreservesExistingMarkdown() {
        String markdown = "- Ý thứ nhất.\n- Ý thứ hai.";

        assertEquals(markdown, service.formatAnswerForDisplay(markdown, "list"));
    }

    @Test
    void firstQuestionUsesOriginalQueryWithoutCallingRewriteModel() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
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
        when(learningScope.requireAccessibleCourse(courseId, userId, false))
                .thenReturn(course(courseId, semesterId));
        when(learningScope.requireActiveWorkspace(courseId))
                .thenReturn(workspace(workspaceId, courseId));
        when(documents.findByCourseIdAndProcessingStatusAndIndexingStatusOrderByUploadedAtDesc(
                courseId, "PROCESSED", "INDEXED"))
                .thenReturn(List.of(available));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setMessageId(UUID.randomUUID());
            return message;
        });
        when(retrieval.retrieve(any())).thenAnswer(invocation -> {
            com.courseqa.model.dto.RagDto.RetrievalRequest request = invocation.getArgument(0);
            assertEquals("Vat chat la gi?", request.queryText);
            com.courseqa.model.dto.RagDto.RetrievalResponse response =
                    new com.courseqa.model.dto.RagDto.RetrievalResponse();
            com.courseqa.model.dto.RagDto.RetrievedChunk first =
                    new com.courseqa.model.dto.RagDto.RetrievedChunk();
            first.chunkId = UUID.randomUUID();
            first.documentId = available.getDocumentId();
            first.content = "Vat chat la cai ton tai khach quan.";
            first.similarityScore = 0.80;
            com.courseqa.model.dto.RagDto.RetrievedChunk second =
                    new com.courseqa.model.dto.RagDto.RetrievedChunk();
            second.chunkId = UUID.randomUUID();
            second.documentId = available.getDocumentId();
            second.content = "Y thuc phan anh vat chat.";
            second.similarityScore = 0.70;
            response.answerable = true;
            response.results = List.of(first, second);
            response.embeddingModelName = "BAAI/bge-m3";
            return response;
        });
        PythonAiDto.GenerateResponse generated = new PythonAiDto.GenerateResponse();
        generated.answer = "Vat chat la cai ton tai khach quan.";
        generated.provider_used = "local-base";
        generated.generation_mode = "BASE_RAG";
        generated.base_model = "Qwen/Qwen2.5-1.5B-Instruct";
        generated.sources = List.of();
        generated.used_chunk_ids = List.of();
        when(ai.callGenerate(any(), any())).thenReturn(generated);

        service.askQuestion(sessionId, "Vat chat la gi?", "RAG");

        verify(ai, never()).callRewriteQuery(any());
        verify(retrieval).retrieve(any());
        org.mockito.ArgumentCaptor<PythonAiDto.GenerateRequest> generateRequest =
                org.mockito.ArgumentCaptor.forClass(PythonAiDto.GenerateRequest.class);
        verify(ai).callGenerate(generateRequest.capture(), any());
        assertEquals("definition", generateRequest.getValue().answer_profile);
        assertEquals("SHORT", generateRequest.getValue().answer_depth);
    }

    @Test
    void fineTunedModeCallsTrainedModelWithoutDocumentRetrieval() {
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
        PythonAiDto.ChatFinetunedResponse modelResponse = new PythonAiDto.ChatFinetunedResponse();
        modelResponse.answer = "Câu trả lời từ data đã train.";
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        UserRole adminRole = new UserRole();
        adminRole.setRoleName("ADMIN");
        when(roles.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of(adminRole));
        when(learningScope.requireAccessibleCourse(courseId, userId, true)).thenReturn(course(courseId, semesterId));
        when(learningScope.requireActiveWorkspace(courseId)).thenReturn(workspace(UUID.randomUUID(), courseId));
        when(documents.findByCourseIdAndProcessingStatusAndIndexingStatusOrderByUploadedAtDesc(
                courseId, "PROCESSED", "INDEXED"))
                .thenReturn(List.of(available));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setMessageId(UUID.randomUUID());
            return message;
        });
        when(ai.callChatFinetuned(any(), any())).thenReturn(modelResponse);

        ChatDto.AskResponse response = service.askQuestion(sessionId,
                "Triết học Mác - Lênin là gì?", "FINE_TUNED");

        assertEquals("FINE_TUNED_ONLY", response.generationMode);
        assertEquals("Câu trả lời từ data đã train.", response.answer);
        verify(retrieval, never()).retrieve(any());
        verify(ai).callChatFinetuned(any(), any());
    }

    @Test
    void fineTunedEvaluationBatchSkipsRetrievalAndForwardsUnverifiedAcknowledgement() {
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
        session.setSessionTitle("Evaluation");
        CourseDocument available = document(UUID.randomUUID(), courseId, "PROCESSED");
        available.setOriginalFilename("triethoc.pdf");
        UserRole adminRole = new UserRole();
        adminRole.setRoleName("ADMIN");

        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(roles.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of(adminRole));
        when(learningScope.requireAccessibleCourse(courseId, userId, true))
                .thenReturn(course(courseId, semesterId));
        when(learningScope.requireActiveWorkspace(courseId))
                .thenReturn(workspace(UUID.randomUUID(), courseId));
        when(documents.findByCourseIdAndProcessingStatusAndIndexingStatusOrderByUploadedAtDesc(
                courseId, "PROCESSED", "INDEXED"))
                .thenReturn(List.of(available));
        when(documents.findAllById(List.of(available.getDocumentId())))
                .thenReturn(List.of(available));
        when(messages.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getMessageId() == null) message.setMessageId(UUID.randomUUID());
            return message;
        });
        when(ai.callChatFinetunedBatch(any())).thenAnswer(invocation -> {
            PythonAiDto.ChatFinetunedBatchRequest request = invocation.getArgument(0);
            assertTrue(Boolean.TRUE.equals(request.allow_unverified));
            assertEquals(1, request.items.size());
            PythonAiDto.ChatFinetunedBatchResult result = new PythonAiDto.ChatFinetunedBatchResult();
            result.request_id = request.items.get(0).request_id;
            result.answer = "Câu trả lời từ LoRA.";
            result.provider_used = "local-lora";
            result.base_model = "Qwen/Qwen2.5-1.5B-Instruct";
            result.adapter_version = "qwen2.5-1.5b-triethoc-lora-v1";
            result.generation_mode = "FINE_TUNED_ONLY";
            result.verification_status = "UNVERIFIED";
            result.quality_gate_passed = false;
            PythonAiDto.ChatFinetunedBatchResponse response =
                    new PythonAiDto.ChatFinetunedBatchResponse();
            response.items = List.of(result);
            return response;
        });

        List<ChatDto.AskResponse> responses = service.askEvaluationBatch(
                sessionId, List.of("Vật chất là gì?"), "FINE_TUNED", true);

        assertEquals(1, responses.size());
        assertEquals("Câu trả lời từ LoRA.", responses.get(0).answer);
        assertEquals("UNVERIFIED", responses.get(0).modelVerificationStatus);
        verify(retrieval, never()).retrieve(any());
        verify(ai).callChatFinetunedBatch(any());
    }

    @Test
    void requireSessionOwnerThrowsForbiddenForOtherUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setChatSessionId(sessionId);
        session.setUserId(userId);
        session.setIsActive(true);

        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.requireSessionOwner(sessionId, otherUserId));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void pinAndRenameSessionUpdatesProperties() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setChatSessionId(sessionId);
        session.setUserId(userId);
        session.setIsActive(true);
        session.setSessionTitle("Old Title");
        session.setIsPinned(false);

        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));

        ChatDto.SessionResponse renamed = service.renameSession(sessionId, userId, "New Title");
        assertEquals("New Title", renamed.getSessionTitle());

        ChatDto.SessionResponse pinned = service.pinSession(sessionId, userId, true);
        assertEquals(true, pinned.getIsPinned());

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
