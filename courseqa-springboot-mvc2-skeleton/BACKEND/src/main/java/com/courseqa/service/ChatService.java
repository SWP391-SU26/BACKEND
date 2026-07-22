package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.ChatSessionDocument;
import com.courseqa.model.entity.Course;
import com.courseqa.model.entity.CourseDocument;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.SemesterWorkspace;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.ChatSessionDocumentRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.CourseMembershipRepository;
import com.courseqa.repository.UserRoleRepository;
import com.courseqa.repository.CourseRepository;
import com.courseqa.repository.SemesterWorkspaceRepository;
import com.courseqa.repository.CourseDocumentRepository;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String OUT_OF_SCOPE_MESSAGE =
            "Không tìm thấy nội dung phù hợp trong tài liệu của môn học.";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatSessionDocumentRepository chatSessionDocumentRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;
    private final AIClientService aiClientService;
    private final AnswerCitationRepository answerCitationRepository;
    private final RetrievalService retrievalService;
    private final CourseMembershipRepository courseMembershipRepository;
    private final UserRoleRepository userRoleRepository;
    private final CourseRepository courseRepository;
    private final SemesterWorkspaceRepository semesterWorkspaceRepository;
    private final CourseDocumentRepository courseDocumentRepository;
    private final LearningScopeService learningScopeService;
    private final PersonalWorkspaceService personalWorkspaceService;
    private final QuestionScopeGuard questionScopeGuard;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatSessionDocumentRepository chatSessionDocumentRepository,
            ChatMessageRepository chatMessageRepository,
            CourseWorkspaceRepository courseWorkspaceRepository,
            AIClientService aiClientService,
            AnswerCitationRepository answerCitationRepository,
            RetrievalService retrievalService,
            CourseMembershipRepository courseMembershipRepository,
            UserRoleRepository userRoleRepository,
            CourseRepository courseRepository,
            SemesterWorkspaceRepository semesterWorkspaceRepository,
            CourseDocumentRepository courseDocumentRepository,
            LearningScopeService learningScopeService,
            PersonalWorkspaceService personalWorkspaceService,
            QuestionScopeGuard questionScopeGuard) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatSessionDocumentRepository = chatSessionDocumentRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
        this.aiClientService = aiClientService;
        this.answerCitationRepository = answerCitationRepository;
        this.retrievalService = retrievalService;
        this.courseMembershipRepository = courseMembershipRepository;
        this.userRoleRepository = userRoleRepository;
        this.courseRepository = courseRepository;
        this.semesterWorkspaceRepository = semesterWorkspaceRepository;
        this.courseDocumentRepository = courseDocumentRepository;
        this.learningScopeService = learningScopeService;
        this.personalWorkspaceService = personalWorkspaceService;
        this.questionScopeGuard = questionScopeGuard;
    }

    public ChatSession createSession(UUID userId, UUID courseId, boolean admin, String requestedTitle) {
        return createSession(userId, "COURSE", null, courseId, List.of(), admin, requestedTitle);
    }

    public ChatSession createSession(UUID userId, String requestedScopeType, UUID requestedSemesterId,
            UUID courseId, List<UUID> requestedDocumentIds, boolean admin, String requestedTitle) {
        String scopeType = normalizeScopeType(requestedScopeType);
        Course course = null;
        CourseWorkspace workspace = null;
        UUID semesterId = requestedSemesterId;
        List<CourseDocument> selectedDocuments = List.of();

        if ("PERSONAL".equals(scopeType)) {
            selectedDocuments = validatePersonalDocuments(userId, requestedDocumentIds);
            workspace = personalWorkspaceService.getOrCreate(userId);
            courseId = null;
            semesterId = null;
        } else if ("SEMESTER".equals(scopeType)) {
            if (semesterId == null) badRequest("semesterId is required for SEMESTER scope.");
            if (learningScopeService.accessibleCoursesInSemester(semesterId, userId, admin).isEmpty()) {
                conflict("This semester has no available processed documents for chat.");
            }
        } else {
            if (courseId == null) badRequest("courseId is required for COURSE and DOCUMENTS scopes.");
            course = learningScopeService.requireAccessibleCourse(courseId, userId, admin);
            semesterId = course.getSemesterWorkspaceId();
            if (requestedSemesterId != null && !requestedSemesterId.equals(semesterId)) {
                badRequest("The selected course does not belong to the selected semester.");
            }
            workspace = learningScopeService.requireActiveWorkspace(courseId);
            if ("DOCUMENTS".equals(scopeType)) {
                selectedDocuments = validateSelectedDocuments(courseId, requestedDocumentIds);
            }
        }

        log.info("Creating chat session for userId: {}, scope: {}, courseId: {}, semesterId: {}",
                userId, scopeType, courseId, semesterId);
        ChatSession newSession = new ChatSession();
        newSession.setUserId(userId);
        newSession.setWorkspaceId(workspace == null ? null : workspace.getWorkspaceId());
        newSession.setCourseId(course == null ? null : course.getCourseId());
        newSession.setSemesterWorkspaceId(semesterId);
        newSession.setScopeType(scopeType);
        newSession.setSessionTitle(requestedTitle == null || requestedTitle.isBlank() ? "New conversation" : truncate(requestedTitle.trim(), 60));
        newSession.setIsActive(true);
        newSession.setStartedAt(LocalDateTime.now());
        newSession.setUpdatedAt(LocalDateTime.now());

        ChatSession savedSession = chatSessionRepository.save(newSession);
        for (CourseDocument document : selectedDocuments) {
            ChatSessionDocument selection = new ChatSessionDocument();
            selection.setChatSessionId(savedSession.getChatSessionId());
            selection.setDocumentId(document.getDocumentId());
            chatSessionDocumentRepository.save(selection);
        }
        log.info("Created new chat session: {}", savedSession.getChatSessionId());
        return savedSession;
    }

    public ChatSession createEvaluationSession(UUID userId, UUID courseId, UUID semesterId, List<UUID> documentIds) {
        return createSession(userId, "DOCUMENTS", semesterId, courseId, documentIds, true,
                "Evaluation benchmark");
    }

    public List<ChatDto.SessionResponse> getSessions(UUID userId, UUID semesterId, UUID courseId,
            String requestedScopeType, boolean admin) {
        List<ChatSession> sessions;
        if ("PERSONAL".equalsIgnoreCase(requestedScopeType)) {
            sessions = chatSessionRepository.findByUserIdAndScopeTypeAndIsActiveTrueOrderByUpdatedAtDesc(userId, "PERSONAL");
        } else if (semesterId != null) {
            learningScopeService.requireAccessibleSemester(semesterId);
            sessions = chatSessionRepository.findByUserIdAndSemesterWorkspaceIdAndIsActiveTrueOrderByUpdatedAtDesc(userId, semesterId);
        } else if (courseId != null) {
            learningScopeService.requireAccessibleCourse(courseId, userId, admin);
            sessions = chatSessionRepository.findByUserIdAndCourseIdAndIsActiveTrueOrderByUpdatedAtDesc(userId, courseId);
        } else {
            badRequest("semesterId, courseId, or PERSONAL scopeType is required.");
            return List.of();
        }
        return sessions.stream().map(this::toSessionResponse).toList();
    }

    public void deleteSession(UUID sessionId, UUID userId) {
        requireSessionOwner(sessionId, userId);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found: " + sessionId));
        session.setIsActive(false); session.setUpdatedAt(LocalDateTime.now()); chatSessionRepository.save(session);
    }

    public List<ChatMessage> getHistory(UUID sessionId) {
        log.info("Fetching chat history for sessionId: {}", sessionId);

        chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found with id: " + sessionId));

       // Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"));
       Pageable pageable = PageRequest.of(0, 50);
        return chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId, pageable)
                .getContent();
    }

    public void requireSessionOwner(UUID sessionId, UUID userId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found with id: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "This chat session belongs to another user.");
        }
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw new ResourceNotFoundException("ChatSession not found with id: " + sessionId);
        }
    }

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question) {
        return askQuestion(sessionId, question, "RAG");
    }

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question, String requestedAnswerMode) {
        return askQuestion(sessionId, question, requestedAnswerMode, false);
    }

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question, String requestedAnswerMode, boolean strict) {
        String answerMode = normalizeAnswerMode(requestedAnswerMode);
        log.info("askQuestion - sessionId: {}, mode: {}, question: {}", sessionId, answerMode, question);

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found: " + sessionId));
        boolean admin = userRoleRepository.findByUserIdAndIsActiveTrue(session.getUserId()).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleName()));
        ResolvedScope resolvedScope = resolveScope(session, admin);
        if (session.getSessionTitle() == null || session.getSessionTitle().isBlank() || "New conversation".equals(session.getSessionTitle())) {
            session.setSessionTitle(truncate(question.trim(), 60));
        }
        session.setUpdatedAt(LocalDateTime.now()); chatSessionRepository.save(session);

        ChatMessage savedUserMessage = saveMessage(sessionId, "user", question);

        if (isGreeting(question)) {
            String greeting = "Chào bạn! Hãy đặt câu hỏi về phạm vi tài liệu bạn đã chọn nhé.";
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", greeting, "GREETING");
            return new ChatDto.AskResponse(sessionId, savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(), greeting, "RAG", "local", "GREETING", null, List.of());
        }

        QuestionScopeGuard.GuardDecision preCheck = questionScopeGuard.preCheck(question);
        if (!preCheck.allowed()) {
            return guardedResponse(sessionId, savedUserMessage, preCheck.message(), answerMode, null, null);
        }

        RagDto.RetrievalResponse retrieval = retrieveFromJavaSql(session, resolvedScope, savedUserMessage, question);
        QuestionScopeGuard.GuardDecision retrievalCheck = questionScopeGuard.postRetrievalCheck(question, retrieval);
        if (!retrievalCheck.allowed()) {
            return guardedResponse(sessionId, savedUserMessage, retrievalCheck.message(), answerMode,
                    retrieval.embeddingModelName, retrieval.retrievalQueryId);
        }

        if ("FINE_TUNED".equals(answerMode)) {
            return answerWithFineTunedModel(sessionId, savedUserMessage, question, strict);
        }

        if (!Boolean.TRUE.equals(retrieval.answerable) || retrieval.results == null || retrieval.results.isEmpty()) {
            String message = firstNonBlank(retrieval.noAnswerReason, OUT_OF_SCOPE_MESSAGE);
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", message, "LOCAL_EXTRACTIVE");
            return new ChatDto.AskResponse(
                    sessionId,
                    savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(),
                    message,
                    "RAG",
                    retrieval.embeddingModelName,
                    "LOCAL_EXTRACTIVE",
                    retrieval.retrievalQueryId,
                    new ArrayList<>()
            );
        }

        PythonAiDto.GenerateResponse generated;
        try {
            generated = aiClientService.callGenerate(
                    toGenerateRequest(question, retrieval.results, strict),
                    PythonAiDto.GenerateResponse.class
            );
        } catch (Exception exception) {
            log.error("Python /api/generate failed for sessionId {}: {}", sessionId, exception.getMessage());
            if (strict) {
                throw new IllegalStateException("Strict RAG generation failed: " + exception.getMessage(), exception);
            }
            String answer = fallbackAnswer(retrieval.results);
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", answer, "LOCAL_FALLBACK");
            List<ChatDto.CitationItem> citations = saveCitations(assistantMessage, retrieval.results, List.of());
            return new ChatDto.AskResponse(
                    sessionId,
                    savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(),
                    answer,
                    "RAG",
                    retrieval.embeddingModelName,
                    "LOCAL_FALLBACK",
                    retrieval.retrievalQueryId,
                    citations
            );
        }

        String answer = Boolean.TRUE.equals(generated.is_out_of_scope)
                ? OUT_OF_SCOPE_MESSAGE
                : (generated.answer == null || generated.answer.isBlank() ? OUT_OF_SCOPE_MESSAGE : generated.answer);

        ChatMessage savedAssistantMessage = saveMessage(sessionId, "assistant", answer, "LOCAL_EXTRACTIVE");
        List<ChatDto.CitationItem> citations = OUT_OF_SCOPE_MESSAGE.equals(answer)
                ? new ArrayList<>()
                : saveCitations(savedAssistantMessage, retrieval.results, generated.sources);

        return new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                savedAssistantMessage.getMessageId(),
                answer,
                "RAG",
                retrieval.embeddingModelName,
                "LOCAL_EXTRACTIVE",
                retrieval.retrievalQueryId,
                citations
        );
    }

    /**
     * Benchmark-only path. Retrieval and audit rows are still produced per question,
     * while model inference is amortized across one GPU batch.
     */
    public List<ChatDto.AskResponse> askEvaluationBatch(
            UUID sessionId, List<String> questions, String requestedAnswerMode) {
        if (questions == null || questions.isEmpty()) return List.of();
        String answerMode = normalizeAnswerMode(requestedAnswerMode);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found: " + sessionId));
        boolean admin = userRoleRepository.findByUserIdAndIsActiveTrue(session.getUserId()).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleName()));
        ResolvedScope resolvedScope = resolveScope(session, admin);
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        List<BenchmarkQuestionContext> prepared = new ArrayList<>();
        for (String question : questions) {
            ChatMessage userMessage = saveMessage(sessionId, "user", question);
            QuestionScopeGuard.GuardDecision guard = questionScopeGuard.preCheck(question);
            RagDto.RetrievalResponse retrieval = null;
            if (guard.allowed()) {
                retrieval = retrieveFromJavaSql(session, resolvedScope, userMessage, question);
                guard = questionScopeGuard.postRetrievalCheck(question, retrieval);
            }
            prepared.add(new BenchmarkQuestionContext(question, userMessage, retrieval, guard));
        }
        return "FINE_TUNED".equals(answerMode)
                ? answerFineTunedEvaluationBatch(sessionId, prepared)
                : answerRagEvaluationBatch(sessionId, prepared);
    }

    private List<ChatDto.AskResponse> answerFineTunedEvaluationBatch(
            UUID sessionId, List<BenchmarkQuestionContext> prepared) {
        PythonAiDto.ChatFinetunedBatchRequest request = new PythonAiDto.ChatFinetunedBatchRequest();
        request.strict = true;
        request.items = prepared.stream().filter(item -> item.guard().allowed()).map(item -> {
            PythonAiDto.ChatFinetunedBatchItem batchItem = new PythonAiDto.ChatFinetunedBatchItem();
            batchItem.request_id = item.userMessage().getMessageId().toString();
            batchItem.question = item.question();
            return batchItem;
        }).toList();

        Map<String, PythonAiDto.ChatFinetunedBatchResult> byId = new HashMap<>();
        if (!request.items.isEmpty()) {
            PythonAiDto.ChatFinetunedBatchResponse generated = aiClientService.callChatFinetunedBatch(request);
            if (generated != null && generated.items != null) {
                generated.items.forEach(item -> byId.put(item.request_id, item));
            }
        }

        List<ChatDto.AskResponse> answers = new ArrayList<>();
        for (BenchmarkQuestionContext item : prepared) {
            if (!item.guard().allowed()) {
                answers.add(guardedResponse(sessionId, item.userMessage(), item.guard().message(),
                        "FINE_TUNED", "scope-guard", item.retrieval() == null ? null : item.retrieval().retrievalQueryId));
                continue;
            }
            String requestId = item.userMessage().getMessageId().toString();
            PythonAiDto.ChatFinetunedBatchResult result = byId.get(requestId);
            if (result == null || result.error != null || result.answer == null || result.answer.isBlank()) {
                throw new IllegalStateException("Fine-tuned batch did not return a valid answer for " + requestId);
            }
            ChatMessage assistant = saveMessage(sessionId, "assistant", result.answer, "FINE_TUNED");
            answers.add(new ChatDto.AskResponse(
                    sessionId, item.userMessage().getMessageId(), assistant.getMessageId(), result.answer,
                    "FINE_TUNED", "qwen-rag-lora", "FINE_TUNED", null, new ArrayList<>()));
        }
        return answers;
    }

    private List<ChatDto.AskResponse> answerRagEvaluationBatch(
            UUID sessionId, List<BenchmarkQuestionContext> prepared) {
        PythonAiDto.GenerateBatchRequest request = new PythonAiDto.GenerateBatchRequest();
        request.strict = true;
        request.items = prepared.stream()
                .filter(item -> item.guard().allowed()
                        && item.retrieval() != null
                        && Boolean.TRUE.equals(item.retrieval().answerable)
                        && item.retrieval().results != null
                        && !item.retrieval().results.isEmpty())
                .map(item -> {
                    PythonAiDto.GenerateBatchItem batchItem = new PythonAiDto.GenerateBatchItem();
                    batchItem.request_id = item.userMessage().getMessageId().toString();
                    batchItem.question = item.question();
                    batchItem.contexts = item.retrieval().results.stream().map(this::toGenerateContext).toList();
                    return batchItem;
                }).toList();

        Map<String, PythonAiDto.GenerateBatchResult> byId = new HashMap<>();
        if (!request.items.isEmpty()) {
            PythonAiDto.GenerateBatchResponse generated = aiClientService.callGenerateBatch(request);
            if (generated != null && generated.items != null) {
                generated.items.forEach(item -> byId.put(item.request_id, item));
            }
        }

        List<ChatDto.AskResponse> answers = new ArrayList<>();
        for (BenchmarkQuestionContext item : prepared) {
            if (!item.guard().allowed()) {
                RagDto.RetrievalResponse retrieval = item.retrieval();
                answers.add(guardedResponse(sessionId, item.userMessage(), item.guard().message(),
                        "RAG", retrieval == null ? null : retrieval.embeddingModelName,
                        retrieval == null ? null : retrieval.retrievalQueryId));
                continue;
            }
            RagDto.RetrievalResponse retrieval = item.retrieval();
            if (retrieval == null || !Boolean.TRUE.equals(retrieval.answerable)
                    || retrieval.results == null || retrieval.results.isEmpty()) {
                String answer = retrieval == null
                        ? OUT_OF_SCOPE_MESSAGE
                        : firstNonBlank(retrieval.noAnswerReason, OUT_OF_SCOPE_MESSAGE);
                ChatMessage assistant = saveMessage(sessionId, "assistant", answer, "LOCAL_EXTRACTIVE");
                answers.add(new ChatDto.AskResponse(
                        sessionId, item.userMessage().getMessageId(), assistant.getMessageId(), answer,
                        "RAG", retrieval == null ? null : retrieval.embeddingModelName,
                        "LOCAL_EXTRACTIVE", retrieval == null ? null : retrieval.retrievalQueryId,
                        new ArrayList<>()));
                continue;
            }

            String requestId = item.userMessage().getMessageId().toString();
            PythonAiDto.GenerateBatchResult result = byId.get(requestId);
            if (result == null || result.error != null) {
                throw new IllegalStateException("RAG batch did not return a valid answer for " + requestId);
            }
            String answer = Boolean.TRUE.equals(result.is_out_of_scope)
                    ? OUT_OF_SCOPE_MESSAGE
                    : firstNonBlank(result.answer, OUT_OF_SCOPE_MESSAGE);
            ChatMessage assistant = saveMessage(sessionId, "assistant", answer, "LOCAL_EXTRACTIVE");
            List<ChatDto.CitationItem> citations = OUT_OF_SCOPE_MESSAGE.equals(answer)
                    ? new ArrayList<>()
                    : saveCitations(assistant, retrieval.results,
                            result.sources == null ? List.of() : result.sources);
            answers.add(new ChatDto.AskResponse(
                    sessionId, item.userMessage().getMessageId(), assistant.getMessageId(), answer,
                    "RAG", retrieval.embeddingModelName, "LOCAL_EXTRACTIVE",
                    retrieval.retrievalQueryId, citations));
        }
        return answers;
    }

    private record BenchmarkQuestionContext(
            String question, ChatMessage userMessage, RagDto.RetrievalResponse retrieval,
            QuestionScopeGuard.GuardDecision guard) { }

    private ChatDto.AskResponse guardedResponse(UUID sessionId, ChatMessage savedUserMessage, String message,
            String answerMode, String modelName, UUID retrievalQueryId) {
        String responseMode = "FINE_TUNED".equals(answerMode) ? "FINE_TUNED" : "RAG";
        String generationMode = "SCOPE_GUARD";
        ChatMessage assistantMessage = saveMessage(sessionId, "assistant", message, generationMode);
        return new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                assistantMessage.getMessageId(),
                message,
                responseMode,
                modelName == null ? "scope-guard" : modelName,
                generationMode,
                retrievalQueryId,
                new ArrayList<>()
        );
    }

    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max).trim(); }

    private ChatDto.AskResponse answerWithFineTunedModel(UUID sessionId, ChatMessage savedUserMessage,
            String question, boolean strict) {
        return answerWithoutRetrieval(sessionId, savedUserMessage, question, "FINE_TUNED", "qwen-rag-lora", strict);
    }

    private ChatDto.AskResponse answerWithoutRetrieval(
            UUID sessionId,
            ChatMessage savedUserMessage,
            String question,
            String responseMode,
            String modelName,
            boolean strict
    ) {
        PythonAiDto.ChatFinetunedRequest request = new PythonAiDto.ChatFinetunedRequest();
        request.question = question;
        request.strict = strict;

        String answer;
        try {
            PythonAiDto.ChatFinetunedResponse response = aiClientService.callChatFinetuned(
                    request,
                    PythonAiDto.ChatFinetunedResponse.class
            );
            answer = response == null || response.answer == null || response.answer.isBlank()
                    ? "The model did not return an answer."
                    : response.answer;
        } catch (Exception exception) {
            log.error("Python /ai/chat-finetuned failed for sessionId {}: {}", sessionId, exception.getMessage());
            if (strict) {
                throw new IllegalStateException("Fine-tuned model is not ready: " + exception.getMessage(), exception);
            }
            answer = "The fine-tuned model is not ready on this machine. Check the local model or switch back to RAG.";
        }

        ChatMessage assistantMessage = saveMessage(sessionId, "assistant", answer, "FINE_TUNED");
        return new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                assistantMessage.getMessageId(),
                answer,
                responseMode,
                modelName,
                "FINE_TUNED",
                null,
                new ArrayList<>()
        );
    }

    private String normalizeAnswerMode(String requestedAnswerMode) {
        if (requestedAnswerMode == null || requestedAnswerMode.isBlank()) {
            return "RAG";
        }
        String normalized = requestedAnswerMode.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        if ("FINE_TUNED".equals(normalized) || "FINETUNED".equals(normalized) || "FINE_TUNING".equals(normalized)) {
            return "FINE_TUNED";
        }
        return "RAG";
    }

    private RagDto.RetrievalResponse retrieveFromJavaSql(ChatSession session, ResolvedScope scope,
            ChatMessage userMessage, String question) {
        RagDto.RetrievalRequest request = new RagDto.RetrievalRequest();
        request.chatSessionId = session.getChatSessionId();
        request.userMessageId = userMessage.getMessageId();
        request.workspaceId = scope.primaryWorkspaceId();
        request.workspaceIds = scope.workspaceIds();
        request.documentIds = scope.documentIds();
        request.semesterId = session.getSemesterWorkspaceId();
        request.scopeType = normalizedSessionScope(session);
        request.queryText = question;
        request.embeddingModelId = session.getSelectedEmbeddingModelId();
        request.topK = needsExpandedContext(question) ? 40 : 5;
        request.similarityThreshold = RetrievalService.DEFAULT_SIMILARITY_THRESHOLD;
        return retrievalService.retrieve(request);
    }

    private boolean needsExpandedContext(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = normalizeText(question);
        return isSummaryQuestion(question)
                || normalized.contains("tat ca")
                || normalized.contains("toan bo")
                || normalized.contains("liet ke")
                || normalized.contains("danh sach")
                || normalized.contains("tu vung")
                || normalized.contains("ngu phap")
                || normalized.contains("mau cau")
                || normalized.contains("vi du")
                || normalized.contains("bai tap");
    }

    private boolean isSummaryQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = normalizeText(question);
        return normalized.contains("tong hop")
                || normalized.contains("tom tat")
                || normalized.contains("summary")
                || normalized.contains("summarize")
                || normalized.contains("noi dung chinh");
    }

    private String normalizeText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private PythonAiDto.GenerateRequest toGenerateRequest(String question, List<RagDto.RetrievedChunk> chunks,
            boolean strict) {
        PythonAiDto.GenerateRequest request = new PythonAiDto.GenerateRequest();
        request.question = question;
        request.strict = strict;
        request.contexts = chunks.stream()
                .map(this::toGenerateContext)
                .toList();
        return request;
    }

    private PythonAiDto.GenerateContext toGenerateContext(RagDto.RetrievedChunk chunk) {
        PythonAiDto.GenerateContext context = new PythonAiDto.GenerateContext();
        context.chunk_id = chunk.chunkId == null ? null : chunk.chunkId.toString();
        context.document_id = chunk.documentId == null ? null : chunk.documentId.toString();
        context.filename = firstNonBlank(chunk.filename, chunk.documentTitle);
        context.page = chunk.pageStart;
        context.content = chunk.content;
        return context;
    }

    private List<ChatDto.CitationItem> saveCitations(
            ChatMessage assistantMessage,
            List<RagDto.RetrievedChunk> retrievedChunks,
            List<Map<String, Object>> pythonSources) {
        List<RagDto.RetrievedChunk> citedChunks = selectCitedChunks(retrievedChunks, pythonSources);
        List<ChatDto.CitationItem> citationItems = new ArrayList<>();

        for (int i = 0; i < citedChunks.size(); i++) {
            RagDto.RetrievedChunk chunk = citedChunks.get(i);
            AnswerCitation citation = new AnswerCitation();
            citation.setAssistantMessageId(assistantMessage.getMessageId());
            citation.setRetrievalResultId(chunk.retrievalResultId);
            citation.setDocumentId(chunk.documentId);
            citation.setChunkId(chunk.chunkId);
            citation.setCitationOrder(i + 1);
            citation.setDocumentTitle(firstNonBlank(chunk.documentTitle, chunk.filename));
            citation.setPageStart(chunk.pageStart);
            citation.setPageEnd(chunk.pageEnd == null ? chunk.pageStart : chunk.pageEnd);
            citation.setQuoteText(preview(chunk.content));
            citation.setCreatedAt(LocalDateTime.now());
            answerCitationRepository.save(citation);

            citationItems.add(new ChatDto.CitationItem(
                    citation.getCitationId(),
                    citation.getAssistantMessageId(),
                    citation.getRetrievalResultId(),
                    citation.getChunkId(),
                    citation.getDocumentId(),
                    citation.getDocumentTitle(),
                    citation.getPageStart(),
                    citation.getPageEnd(),
                    citation.getQuoteText()
            ));
        }

        return citationItems;
    }

    private List<RagDto.RetrievedChunk> selectCitedChunks(
            List<RagDto.RetrievedChunk> retrievedChunks,
            List<Map<String, Object>> pythonSources) {
        if (pythonSources == null || pythonSources.isEmpty()) {
            return retrievedChunks == null ? List.of() : retrievedChunks.stream().limit(3).toList();
        }

        List<UUID> sourceChunkIds = pythonSources.stream()
                .map(source -> parseUuid(source.get("chunk_id")))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (sourceChunkIds.isEmpty()) {
            return List.of();
        }

        return sourceChunkIds.stream()
                .distinct()
                .map(sourceChunkId -> retrievedChunks.stream()
                        .filter(chunk -> sourceChunkId.equals(chunk.chunkId))
                        .findFirst()
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .limit(12)
                .toList();
    }

    private ChatMessage saveMessage(UUID sessionId, String role, String content) {
        return saveMessage(sessionId, role, content, null);
    }

    private ChatMessage saveMessage(UUID sessionId, String role, String content, String model) {
        ChatMessage message = new ChatMessage();
        message.setChatSessionId(sessionId);
        message.setSenderRole(role);
        message.setMessageContent(content);
        message.setLlmModel(model);
        message.setCreatedAt(LocalDateTime.now());
        return chatMessageRepository.save(message);
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 280 ? content : content.substring(0, 280);
    }

    private List<CourseDocument> validateSelectedDocuments(UUID courseId, List<UUID> requestedDocumentIds) {
        List<UUID> documentIds = requestedDocumentIds == null
                ? List.of()
                : requestedDocumentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (documentIds.isEmpty()) badRequest("At least one documentId is required for DOCUMENTS scope.");

        List<CourseDocument> selected = courseDocumentRepository.findAllById(documentIds);
        if (selected.size() != documentIds.size()) badRequest("One or more selected documents do not exist.");
        boolean invalid = selected.stream().anyMatch(document ->
                !courseId.equals(document.getCourseId()) || !"PROCESSED".equals(document.getProcessingStatus()));
        if (invalid) badRequest("All selected documents must be processed and belong to the selected course.");
        return selected;
    }

    private List<CourseDocument> validatePersonalDocuments(UUID userId, List<UUID> requestedDocumentIds) {
        List<UUID> documentIds = requestedDocumentIds == null
                ? List.of()
                : requestedDocumentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (documentIds.isEmpty()) badRequest("At least one documentId is required for PERSONAL scope.");
        List<CourseDocument> selected = courseDocumentRepository.findAllById(documentIds);
        if (selected.size() != documentIds.size()) badRequest("One or more selected documents do not exist.");
        boolean invalid = selected.stream().anyMatch(document ->
                !userId.equals(document.getUploadedBy()) || !"PROCESSED".equals(document.getProcessingStatus()));
        if (invalid) badRequest("Personal chat only accepts processed documents uploaded by the current user.");
        return selected;
    }

    private ResolvedScope resolveScope(ChatSession session, boolean admin) {
        String scopeType = normalizedSessionScope(session);
        if ("PERSONAL".equals(scopeType)) {
            List<UUID> selectedIds = chatSessionDocumentRepository.findByChatSessionId(session.getChatSessionId()).stream()
                    .map(ChatSessionDocument::getDocumentId).distinct().toList();
            List<UUID> documentIds = validatePersonalDocuments(session.getUserId(), selectedIds).stream()
                    .map(CourseDocument::getDocumentId).toList();
            CourseWorkspace workspace = personalWorkspaceService.getOrCreate(session.getUserId());
            return new ResolvedScope(workspace.getWorkspaceId(), List.of(workspace.getWorkspaceId()), documentIds);
        }
        if ("SEMESTER".equals(scopeType)) {
            UUID semesterId = session.getSemesterWorkspaceId();
            if (semesterId == null) conflict("This semester chat session is missing its semester scope.");
            List<Course> availableCourses = learningScopeService.accessibleCoursesInSemester(
                    semesterId, session.getUserId(), admin);
            if (availableCourses.isEmpty()) conflict("This semester has no available documents for chat.");
            List<UUID> courseIds = availableCourses.stream().map(Course::getCourseId).toList();
            List<UUID> documentIds = courseDocumentRepository
                    .findByCourseIdInAndProcessingStatus(courseIds, "PROCESSED").stream()
                    .map(CourseDocument::getDocumentId).distinct().toList();
            List<UUID> workspaceIds = availableCourses.stream()
                    .map(course -> learningScopeService.requireActiveWorkspace(course.getCourseId()).getWorkspaceId())
                    .distinct().toList();
            if (documentIds.isEmpty()) conflict("This semester has no processed document available for chat.");
            return new ResolvedScope(null, workspaceIds, documentIds);
        }

        Course course = learningScopeService.requireAccessibleCourse(session.getCourseId(), session.getUserId(), admin);
        CourseWorkspace workspace = learningScopeService.requireActiveWorkspace(course.getCourseId());
        List<UUID> documentIds;
        if ("DOCUMENTS".equals(scopeType)) {
            List<UUID> selectedIds = chatSessionDocumentRepository.findByChatSessionId(session.getChatSessionId()).stream()
                    .map(ChatSessionDocument::getDocumentId).distinct().toList();
            documentIds = validateSelectedDocuments(course.getCourseId(), selectedIds).stream()
                    .map(CourseDocument::getDocumentId).toList();
        } else {
            documentIds = courseDocumentRepository
                    .findByCourseIdAndProcessingStatusOrderByUploadedAtDesc(course.getCourseId(), "PROCESSED").stream()
                    .map(CourseDocument::getDocumentId).distinct().toList();
        }
        if (documentIds.isEmpty()) conflict("This course has no processed document available for chat.");
        return new ResolvedScope(workspace.getWorkspaceId(), List.of(workspace.getWorkspaceId()), documentIds);
    }

    public ChatDto.SessionResponse toSessionResponse(ChatSession session) {
        String scopeType = normalizedSessionScope(session);
        List<UUID> documentIds = Set.of("DOCUMENTS", "PERSONAL").contains(scopeType)
                ? chatSessionDocumentRepository.findByChatSessionId(session.getChatSessionId()).stream()
                        .map(ChatSessionDocument::getDocumentId).distinct().toList()
                : List.of();
        String scopeLabel;
        if ("PERSONAL".equals(scopeType)) {
            scopeLabel = documentIds.size() + " tài liệu cá nhân";
        } else if ("SEMESTER".equals(scopeType)) {
            scopeLabel = semesterWorkspaceRepository.findById(session.getSemesterWorkspaceId())
                    .map(SemesterWorkspace::getSemesterName).orElse("Học kỳ");
        } else {
            Course course = courseRepository.findById(session.getCourseId()).orElse(null);
            String courseLabel = course == null ? "Môn học" : firstNonBlank(course.getCourseCode(), course.getCourseName());
            scopeLabel = "DOCUMENTS".equals(scopeType)
                    ? documentIds.size() + " tài liệu · " + courseLabel
                    : courseLabel;
        }
        return ChatDto.SessionResponse.builder()
                .chatSessionId(session.getChatSessionId())
                .userId(session.getUserId())
                .workspaceId(session.getWorkspaceId())
                .semesterId(session.getSemesterWorkspaceId())
                .courseId(session.getCourseId())
                .scopeType(scopeType)
                .documentIds(documentIds)
                .scopeLabel(scopeLabel)
                .sessionTitle(session.getSessionTitle())
                .isActive(session.getIsActive())
                .startedAt(session.getStartedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private String normalizeScopeType(String requestedScopeType) {
        String value = requestedScopeType == null || requestedScopeType.isBlank()
                ? "COURSE"
                : requestedScopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("PERSONAL", "DOCUMENTS", "COURSE", "SEMESTER").contains(value)) {
            badRequest("scopeType must be PERSONAL, DOCUMENTS, COURSE, or SEMESTER.");
        }
        return value;
    }

    private String normalizedSessionScope(ChatSession session) {
        return normalizeScopeType(session.getScopeType());
    }

    private boolean isGreeting(String question) {
        String normalized = normalizeText(question == null ? "" : question).trim()
                .replaceAll("[!.?]+$", "").trim();
        return Set.of("chao", "xin chao", "hello", "hi", "cam on", "cam on ban", "thank you", "thanks")
                .contains(normalized);
    }

    private String fallbackAnswer(List<RagDto.RetrievedChunk> chunks) {
        StringBuilder answer = new StringBuilder("Mình tìm thấy nội dung liên quan trong tài liệu:\n");
        chunks.stream().limit(3).forEach(chunk -> {
            String content = chunk.content == null ? "" : chunk.content.trim();
            if (content.length() > 700) content = content.substring(0, 700).trim() + "...";
            if (!content.isBlank()) answer.append("\n").append(content);
        });
        return answer.toString().trim();
    }

    private void badRequest(String message) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    private void conflict(String message) {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, message);
    }

    private record ResolvedScope(UUID primaryWorkspaceId, List<UUID> workspaceIds, List<UUID> documentIds) { }
}
