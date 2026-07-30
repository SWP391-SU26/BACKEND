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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
    private static final String FINE_TUNED_REFUSE_MESSAGE =
            "Mình chỉ trả lời trong phạm vi học tập và dữ liệu đã huấn luyện. "
                    + "Câu hỏi này chưa phù hợp với phạm vi đó, bạn hỏi lại về nội dung học tập nhé.";

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

    @FunctionalInterface
    public interface ChatProgressListener {
        void onPhase(String phase);
    }

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
        newSession.setIsPinned(false);
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

    public List<ChatDto.SessionResponse> getSessions(UUID userId, UUID semesterId, UUID courseId,
            String requestedScopeType, String query, boolean admin) {
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
            sessions = chatSessionRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(userId);
        }
        String normalizedQuery = query == null ? "" : normalizeText(query).trim();
        Comparator<ChatSession> historyOrder = Comparator
                .comparing((ChatSession item) -> Boolean.TRUE.equals(item.getIsPinned())).reversed()
                .thenComparing(ChatSession::getPinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return sessions.stream()
                .filter(item -> normalizedQuery.isBlank()
                        || normalizeText(firstNonBlank(item.getSessionTitle(), "")).contains(normalizedQuery))
                .sorted(historyOrder)
                .map(this::toSessionResponse)
                .toList();
    }

    public ChatDto.SessionResponse renameSession(UUID sessionId, UUID userId, String requestedTitle) {
        ChatSession session = ownedSession(sessionId, userId);
        String title = requestedTitle == null ? "" : requestedTitle.trim();
        if (title.isBlank()) badRequest("title is required.");
        session.setSessionTitle(truncate(title, 120));
        session.setUpdatedAt(LocalDateTime.now());
        return toSessionResponse(chatSessionRepository.save(session));
    }

    public ChatDto.SessionResponse pinSession(UUID sessionId, UUID userId, Boolean requestedPinned) {
        ChatSession session = ownedSession(sessionId, userId);
        boolean pinned = Boolean.TRUE.equals(requestedPinned);
        session.setIsPinned(pinned);
        session.setPinnedAt(pinned ? LocalDateTime.now() : null);
        session.setUpdatedAt(LocalDateTime.now());
        return toSessionResponse(chatSessionRepository.save(session));
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
        ownedSession(sessionId, userId);
    }

    private ChatSession ownedSession(UUID sessionId, UUID userId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found with id: " + sessionId));
        if (session.getUserId() == null || !session.getUserId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "This chat session belongs to another user.");
        }
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw new ResourceNotFoundException("ChatSession not found with id: " + sessionId);
        }
        return session;
    }

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question) {
        return askQuestion(sessionId, question, "RAG");
    }

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question, String requestedAnswerMode) {
        String normalizedMode = normalizeAnswerMode(requestedAnswerMode);
        return askQuestion(sessionId, question, normalizedMode, "FINE_TUNED".equals(normalizedMode));
    }

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question, String requestedAnswerMode, boolean strict) {
        return askQuestion(sessionId, question, requestedAnswerMode, strict, phase -> { });
    }

    public ChatDto.AskResponse askQuestion(
            UUID sessionId,
            String question,
            String requestedAnswerMode,
            boolean strict,
            ChatProgressListener progressListener
    ) {
        long startedAt = System.nanoTime();
        ChatProgressListener progress = progressListener == null ? phase -> { } : progressListener;
        String answerMode = normalizeAnswerMode(requestedAnswerMode);
        log.info("askQuestion - sessionId: {}, mode: {}, question: {}", sessionId, answerMode, question);

        progress.onPhase("SCOPE_CHECK");
        long scopeStartedAt = System.nanoTime();
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found: " + sessionId));
        boolean admin = userRoleRepository.findByUserIdAndIsActiveTrue(session.getUserId()).stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getRoleName()));
        if ("FINE_TUNED".equals(answerMode) && !admin) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Fine-tuned-only mode is available only in Admin Research.");
        }
        ResolvedScope resolvedScope = resolveScope(session, admin);
        log.info("AI chat scope completed in {} ms for session {}", elapsedMs(scopeStartedAt), sessionId);
        if (session.getSessionTitle() == null || session.getSessionTitle().isBlank() || "New conversation".equals(session.getSessionTitle())) {
            session.setSessionTitle(truncate(question.trim(), 60));
        }
        session.setUpdatedAt(LocalDateTime.now()); chatSessionRepository.save(session);

        List<PythonAiDto.ChatHistoryItem> recentHistory = recentHistory(sessionId);
        ChatMessage savedUserMessage = saveMessage(sessionId, "user", question);

        if (isGreeting(question)) {
            String greeting = "Chào bạn! Hãy đặt câu hỏi về phạm vi tài liệu bạn đã chọn nhé.";
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", greeting, "GREETING");
            return completeResponse(new ChatDto.AskResponse(sessionId, savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(), greeting, "RAG", "local", "GREETING", null, List.of()),
                    startedAt);
        }

        QuestionScopeGuard.GuardDecision preCheck = questionScopeGuard.preCheck(question);
        if (!preCheck.allowed()) {
            return completeResponse(
                    guardedResponse(sessionId, savedUserMessage, preCheck.message(), answerMode, null, null),
                    startedAt);
        }

        if ("FINE_TUNED".equals(answerMode)) {
            progress.onPhase("GENERATION_START");
            List<String> selectedFilenames = courseDocumentRepository.findAllById(resolvedScope.documentIds()).stream()
                    .map(CourseDocument::getOriginalFilename)
                    .filter(Objects::nonNull)
                    .filter(filename -> !filename.isBlank())
                    .distinct()
                    .toList();
            return completeResponse(answerWithFineTunedModel(
                    sessionId, savedUserMessage, question, strict, selectedFilenames, null), startedAt);
        }

        progress.onPhase("RETRIEVAL");
        long retrievalStartedAt = System.nanoTime();
        RagFlowContext ragFlow = runRetrievalFlow(
                session, resolvedScope, savedUserMessage, question, recentHistory);
        log.info("AI chat retrieval completed in {} ms for session {}",
                elapsedMs(retrievalStartedAt), sessionId);
        RagDto.RetrievalResponse retrieval = ragFlow.retrieval();
        QuestionScopeGuard.GuardDecision retrievalCheck = questionScopeGuard.postRetrievalCheck(question, retrieval);
        if (!retrievalCheck.allowed()) {
            return completeResponse(
                    guardedResponse(sessionId, savedUserMessage, retrievalCheck.message(), answerMode,
                            retrieval.embeddingModelName, retrieval.retrievalQueryId),
                    startedAt);
        }

        if (!Boolean.TRUE.equals(retrieval.answerable) || retrieval.results == null || retrieval.results.isEmpty()) {
            String message = firstNonBlank(retrieval.noAnswerReason, OUT_OF_SCOPE_MESSAGE);
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", message, "OUT_OF_SCOPE");
            return completeResponse(new ChatDto.AskResponse(
                    sessionId,
                    savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(),
                    message,
                    "RAG",
                    retrieval.embeddingModelName,
                    "OUT_OF_SCOPE",
                    retrieval.retrievalQueryId,
                    new ArrayList<>()
            ), startedAt);
        }

        PythonAiDto.GenerateResponse generated;
        try {
            progress.onPhase("GENERATION_START");
            long generationStartedAt = System.nanoTime();
            List<PythonAiDto.ChatHistoryItem> generationHistory =
                    ragFlow.followUp() ? recentHistory : List.of();
            generated = aiClientService.callGenerate(
                    toGenerateRequest(question, ragFlow.standaloneQuery(), generationHistory,
                            ragFlow.answerProfile(), retrieval.results, strict),
                    PythonAiDto.GenerateResponse.class
            );
            log.info("AI chat generation completed in {} ms for session {}",
                    elapsedMs(generationStartedAt), sessionId);
        } catch (Exception exception) {
            log.error("Python /api/generate failed for sessionId {}: {}", sessionId, exception.getMessage());
            String answer = buildLocalFallbackAnswer(retrieval.results);
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", answer, "LOCAL_FALLBACK");
            List<String> usedChunkIds = fallbackChunkIds(retrieval.results);
            List<ChatDto.CitationItem> citations = saveCitations(
                    assistantMessage, retrieval.results, List.of(), usedChunkIds);
            ChatDto.AskResponse response = new ChatDto.AskResponse(
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
            response.providerUsed = "spring-local-fallback";
            response.fallbackReason = classifyAiFailure(exception);
            return completeResponse(response, startedAt);
        }

        String answer = Boolean.TRUE.equals(generated.is_out_of_scope)
                ? OUT_OF_SCOPE_MESSAGE
                : (generated.answer == null || generated.answer.isBlank() ? OUT_OF_SCOPE_MESSAGE : generated.answer);
        if (!OUT_OF_SCOPE_MESSAGE.equals(answer)) {
            answer = formatAnswerForDisplay(answer, ragFlow.answerProfile());
        }

        String providerUsed = firstNonBlank(generated.provider_used, "unknown");
        String generationMode = firstNonBlank(generated.generation_mode, "BASE_RAG");
        ChatMessage savedAssistantMessage = saveMessage(sessionId, "assistant", answer, providerUsed);
        List<ChatDto.CitationItem> citations = OUT_OF_SCOPE_MESSAGE.equals(answer)
                ? new ArrayList<>()
                : saveCitations(savedAssistantMessage, retrieval.results, generated.sources, generated.used_chunk_ids);

        ChatDto.AskResponse response = new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                savedAssistantMessage.getMessageId(),
                answer,
                "RAG",
                firstNonBlank(generated.base_model, retrieval.embeddingModelName),
                generationMode,
                retrieval.retrievalQueryId,
                citations
        );
        applyGenerationMetadata(response, generated.provider_used, generated.base_model,
                generated.adapter_version, generated.embedding_model, generated.dataset_version,
                generated.prompt_version, generated.used_chunk_ids, generated.peak_vram_bytes);
        response.groundingStatus = generated.grounding_status;
        response.fallbackReason = generated.fallback_reason;
        response.groundingScore = generated.grounding_score;
        response.repairAttempted = generated.repair_attempted;
        response.unsupportedSentenceCount = generated.unsupported_sentence_count;
        return completeResponse(response, startedAt);
    }

    /**
     * Benchmark-only path. Retrieval and audit rows are still produced per question,
     * while model inference is amortized across one GPU batch.
     */
    public List<ChatDto.AskResponse> askEvaluationBatch(
            UUID sessionId, List<String> questions, String requestedAnswerMode) {
        return askEvaluationBatch(sessionId, questions, requestedAnswerMode, false);
    }

    public List<ChatDto.AskResponse> askEvaluationBatch(
            UUID sessionId, List<String> questions, String requestedAnswerMode, boolean allowUnverifiedModel) {
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
            String standaloneQuery = question;
            String answerProfile = answerProfile(QuestionIntentAnalyzer.analyze(question));
            if (guard.allowed() && !"FINE_TUNED".equals(answerMode)) {
                RagFlowContext ragFlow = runRetrievalFlow(
                        session, resolvedScope, userMessage, question, List.of());
                retrieval = ragFlow.retrieval();
                standaloneQuery = ragFlow.standaloneQuery();
                answerProfile = ragFlow.answerProfile();
                guard = questionScopeGuard.postRetrievalCheck(question, retrieval);
            }
            prepared.add(new BenchmarkQuestionContext(
                    question, standaloneQuery, answerProfile, userMessage, retrieval, guard));
        }
        List<String> selectedFilenames = courseDocumentRepository.findAllById(resolvedScope.documentIds()).stream()
                .map(CourseDocument::getOriginalFilename)
                .filter(Objects::nonNull)
                .filter(filename -> !filename.isBlank())
                .distinct()
                .toList();
        return "FINE_TUNED".equals(answerMode)
                ? answerFineTunedEvaluationBatch(
                        sessionId, prepared, selectedFilenames, allowUnverifiedModel)
                : answerRagEvaluationBatch(sessionId, prepared);
    }

    private List<ChatDto.AskResponse> answerFineTunedEvaluationBatch(
            UUID sessionId, List<BenchmarkQuestionContext> prepared, List<String> selectedFilenames,
            boolean allowUnverifiedModel) {
        PythonAiDto.ChatFinetunedBatchRequest request = new PythonAiDto.ChatFinetunedBatchRequest();
        request.strict = true;
        request.allow_unverified = allowUnverifiedModel;
        request.items = prepared.stream().filter(item -> item.guard().allowed()).map(item -> {
            PythonAiDto.ChatFinetunedBatchItem batchItem = new PythonAiDto.ChatFinetunedBatchItem();
            batchItem.request_id = item.userMessage().getMessageId().toString();
            batchItem.question = item.question();
            batchItem.document_filenames = selectedFilenames;
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
            boolean outOfScope = Boolean.TRUE.equals(result.is_out_of_scope);
            String generationMode = outOfScope
                    ? "FINE_TUNED_SCOPE"
                    : firstNonBlank(result.generation_mode, "FINE_TUNED_ONLY");
            ChatMessage assistant = saveMessage(sessionId, "assistant", result.answer,
                    firstNonBlank(result.provider_used, generationMode));
            ChatDto.AskResponse response = new ChatDto.AskResponse(
                    sessionId, item.userMessage().getMessageId(), assistant.getMessageId(), result.answer,
                    "FINE_TUNED", firstNonBlank(result.base_model, "unknown"), generationMode,
                    null, new ArrayList<>());
            applyGenerationMetadata(response, result.provider_used, result.base_model, result.adapter_version,
                    null, result.dataset_version, result.prompt_version, List.of(), result.peak_vram_bytes);
            response.modelVerificationStatus = result.verification_status;
            response.qualityGatePassed = result.quality_gate_passed;
            answers.add(response);
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
                    batchItem.standalone_query = item.standaloneQuery();
                    batchItem.history = List.of();
                    batchItem.answer_profile = item.answerProfile();
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
                ChatMessage assistant = saveMessage(sessionId, "assistant", answer, "OUT_OF_SCOPE");
                answers.add(new ChatDto.AskResponse(
                        sessionId, item.userMessage().getMessageId(), assistant.getMessageId(), answer,
                        "RAG", retrieval == null ? null : retrieval.embeddingModelName,
                        "OUT_OF_SCOPE", retrieval == null ? null : retrieval.retrievalQueryId,
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
            String providerUsed = firstNonBlank(result.provider_used, "unknown");
            String generationMode = firstNonBlank(result.generation_mode, "BASE_RAG");
            ChatMessage assistant = saveMessage(sessionId, "assistant", answer, providerUsed);
            List<ChatDto.CitationItem> citations = OUT_OF_SCOPE_MESSAGE.equals(answer)
                    ? new ArrayList<>()
                    : saveCitations(assistant, retrieval.results,
                            result.sources == null ? List.of() : result.sources, result.used_chunk_ids);
            ChatDto.AskResponse response = new ChatDto.AskResponse(
                    sessionId, item.userMessage().getMessageId(), assistant.getMessageId(), answer,
                    "RAG", firstNonBlank(result.base_model, retrieval.embeddingModelName), generationMode,
                    retrieval.retrievalQueryId, citations);
            applyGenerationMetadata(response, result.provider_used, result.base_model, result.adapter_version,
                    result.embedding_model, result.dataset_version, result.prompt_version, result.used_chunk_ids,
                    result.peak_vram_bytes);
            response.groundingStatus = result.grounding_status;
            response.fallbackReason = result.fallback_reason;
            answers.add(response);
        }
        return answers;
    }

    private record BenchmarkQuestionContext(
            String question, String standaloneQuery, String answerProfile,
            ChatMessage userMessage, RagDto.RetrievalResponse retrieval,
            QuestionScopeGuard.GuardDecision guard) { }

    private record RetrievalProfile(int initialTopK, int finalTopK, String answerProfile) { }

    private record RagFlowContext(
            RagDto.RetrievalResponse retrieval,
            String standaloneQuery,
            String answerProfile,
            boolean followUp
    ) { }

    private ChatDto.AskResponse guardedResponse(UUID sessionId, ChatMessage savedUserMessage, String message,
            String answerMode, String modelName, UUID retrievalQueryId) {
        String responseMode = "FINE_TUNED".equals(answerMode) ? "FINE_TUNED" : "RAG";
        String generationMode = "SCOPE_GUARD";
        String responseMessage = "FINE_TUNED".equals(answerMode)
                && QuestionScopeGuard.REFUSE_MESSAGE.equals(message)
                ? FINE_TUNED_REFUSE_MESSAGE
                : message;
        ChatMessage assistantMessage = saveMessage(sessionId, "assistant", responseMessage, generationMode);
        ChatDto.AskResponse response = new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                assistantMessage.getMessageId(),
                responseMessage,
                responseMode,
                modelName == null ? "scope-guard" : modelName,
                generationMode,
                retrievalQueryId,
                new ArrayList<>()
        );
        response.groundingStatus = "OUT_OF_SCOPE";
        response.fallbackReason = "NO_RELEVANT_CONTEXT";
        response.groundingScore = 0.0;
        response.repairAttempted = false;
        response.unsupportedSentenceCount = 0;
        response.usedChunkIds = List.of();
        return response;
    }

    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max).trim(); }

    private ChatDto.AskResponse answerWithFineTunedModel(UUID sessionId, ChatMessage savedUserMessage,
            String question, boolean strict, List<String> selectedFilenames, UUID retrievalQueryId) {
        return answerWithoutRetrieval(sessionId, savedUserMessage, question, "FINE_TUNED", null,
                strict, selectedFilenames, retrievalQueryId);
    }

    private ChatDto.AskResponse answerWithoutRetrieval(
            UUID sessionId,
            ChatMessage savedUserMessage,
            String question,
            String responseMode,
            String modelName,
            boolean strict,
            List<String> selectedFilenames,
            UUID retrievalQueryId
    ) {
        PythonAiDto.ChatFinetunedRequest request = new PythonAiDto.ChatFinetunedRequest();
        request.question = question;
        request.strict = strict;
        request.document_filenames = selectedFilenames;

        String answer;
        boolean outOfScope = false;
        boolean modelReady = true;
        PythonAiDto.ChatFinetunedResponse response = null;
        try {
            response = aiClientService.callChatFinetuned(
                    request,
                    PythonAiDto.ChatFinetunedResponse.class
            );
            answer = response == null || response.answer == null || response.answer.isBlank()
                    ? "Fine-tuned model không trả về nội dung hợp lệ."
                    : response.answer;
            outOfScope = response != null && Boolean.TRUE.equals(response.is_out_of_scope);
            modelReady = response == null || !Boolean.FALSE.equals(response.model_ready);
        } catch (Exception exception) {
            log.error("Python /ai/chat-finetuned failed for sessionId {}: {}", sessionId, exception.getMessage());
            if (strict) {
                throw new IllegalStateException("Fine-tuned model is not ready: " + exception.getMessage(), exception);
            }
            answer = "Fine-tuned model chưa sẵn sàng do lỗi runtime nội bộ. Vui lòng kiểm tra trạng thái model và quality gate.";
            modelReady = false;
        }

        String generationMode = !modelReady
                ? "FINE_TUNED_NOT_READY"
                : (outOfScope ? "FINE_TUNED_SCOPE"
                        : firstNonBlank(response == null ? null : response.generation_mode, "FINE_TUNED_ONLY"));
        String providerUsed = firstNonBlank(response == null ? null : response.provider_used, generationMode);
        ChatMessage assistantMessage = saveMessage(sessionId, "assistant", answer, providerUsed);
        ChatDto.AskResponse result = new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                assistantMessage.getMessageId(),
                answer,
                responseMode,
                firstNonBlank(response == null ? null : response.base_model, modelName),
                generationMode,
                retrievalQueryId,
                new ArrayList<>()
        );
        applyGenerationMetadata(result,
                response == null ? null : response.provider_used,
                response == null ? null : response.base_model,
                response == null ? null : response.adapter_version,
                null,
                response == null ? null : response.dataset_version,
                response == null ? null : response.prompt_version,
                List.of(),
                response == null ? null : response.peak_vram_bytes);
        result.modelVerificationStatus = response == null ? null : response.verification_status;
        result.qualityGatePassed = response == null ? null : response.quality_gate_passed;
        return result;
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

    private RagFlowContext runRetrievalFlow(
            ChatSession session,
            ResolvedScope scope,
            ChatMessage userMessage,
            String question,
            List<PythonAiDto.ChatHistoryItem> history
    ) {
        QuestionIntentAnalyzer.QueryIntent intent = QuestionIntentAnalyzer.analyze(question);
        RetrievalProfile profile = retrievalProfile(intent);
        boolean followUp = needsFollowUpRewrite(question, history);
        String standaloneQuery = followUp
                ? rewriteQuery(question, history, intent, 1, List.of())
                : question;
        RagDto.RetrievalResponse first = retrieveFromJavaSql(
                session, scope, userMessage, question, standaloneQuery, profile.initialTopK());
        RagDto.RetrievalResponse retrieval = limitRetrieval(first, profile.finalTopK());
        return new RagFlowContext(retrieval, standaloneQuery, profile.answerProfile(), followUp);
    }

    private boolean needsFollowUpRewrite(String question, List<PythonAiDto.ChatHistoryItem> history) {
        if (history == null || history.isEmpty() || question == null || question.isBlank()) {
            return false;
        }
        String normalized = normalizeText(question);
        return List.of(
                "dieu do", "noi dung tren", "y tren", "van de nay", "tai sao vay",
                "no la gi", "chung la gi", "that", "the above", "why is that"
        ).stream().anyMatch(normalized::contains);
    }

    private String rewriteQuery(
            String question,
            List<PythonAiDto.ChatHistoryItem> history,
            QuestionIntentAnalyzer.QueryIntent intent,
            int attempt,
            List<String> evidenceHints
    ) {
        PythonAiDto.RewriteQueryRequest request = new PythonAiDto.RewriteQueryRequest();
        request.question = question;
        request.history = history;
        request.intent = answerProfile(intent);
        request.attempt = attempt;
        request.evidence_hints = evidenceHints;
        try {
            PythonAiDto.RewriteQueryResponse response = aiClientService.callRewriteQuery(request);
            return response == null || response.standalone_query == null
                    || response.standalone_query.isBlank()
                    ? question
                    : response.standalone_query.trim();
        } catch (Exception exception) {
            log.warn("Query rewrite attempt {} failed; using original question: {}",
                    attempt, exception.getMessage());
            return question;
        }
    }

    private RagDto.RetrievalResponse retrieveFromJavaSql(
            ChatSession session,
            ResolvedScope scope,
            ChatMessage userMessage,
            String originalQuestion,
            String queryText,
            int topK
    ) {
        RagDto.RetrievalRequest request = new RagDto.RetrievalRequest();
        request.chatSessionId = session.getChatSessionId();
        request.userMessageId = userMessage.getMessageId();
        request.workspaceId = scope.primaryWorkspaceId();
        request.workspaceIds = scope.workspaceIds();
        request.documentIds = scope.documentIds();
        request.semesterId = session.getSemesterWorkspaceId();
        request.scopeType = normalizedSessionScope(session);
        request.originalQueryText = originalQuestion;
        request.queryText = queryText;
        request.embeddingModelId = session.getSelectedEmbeddingModelId();
        request.topK = topK;
        request.similarityThreshold = retrievalService.getConfiguredSimilarityThreshold();
        return retrievalService.retrieve(request);
    }

    private RetrievalProfile retrievalProfile(QuestionIntentAnalyzer.QueryIntent intent) {
        if (intent.summary() || intent.hasSection()) {
            return new RetrievalProfile(16, 12, "summary");
        }
        return switch (intent.form()) {
            case COMPARISON -> new RetrievalProfile(12, 8, "comparison");
            case LIST -> new RetrievalProfile(12, 8, "list");
            case REASONING -> new RetrievalProfile(12, 8, "reasoning");
            case PROCEDURE -> new RetrievalProfile(12, 8, "procedure");
            case DEFINITION -> new RetrievalProfile(8, 5, "definition");
            default -> new RetrievalProfile(8, 5, "factual");
        };
    }

    private String answerProfile(QuestionIntentAnalyzer.QueryIntent intent) {
        return retrievalProfile(intent).answerProfile();
    }

    private boolean hasWeakEvidence(RagDto.RetrievalResponse response) {
        if (response == null || response.results == null || response.results.size() < 2) {
            return true;
        }
        Double topScore = response.results.get(0).similarityScore;
        double minimum = Math.max(retrievalService.getConfiguredSimilarityThreshold() + 0.05, 0.30);
        return topScore == null || topScore < minimum;
    }

    private List<String> evidenceHints(RagDto.RetrievalResponse response) {
        if (response == null || response.results == null) return List.of();
        return response.results.stream()
                .limit(2)
                .map(chunk -> "%s, trang %s: %s".formatted(
                        firstNonBlank(chunk.filename, chunk.documentTitle),
                        chunk.pageStart == null ? "?" : chunk.pageStart,
                        truncate(firstNonBlank(chunk.content, ""), 500)))
                .toList();
    }

    private RagDto.RetrievalResponse limitRetrieval(RagDto.RetrievalResponse source, int limit) {
        if (source == null || source.results == null || source.results.size() <= limit) return source;
        RagDto.RetrievalResponse response = copyRetrievalMetadata(source);
        response.results = source.results.stream().limit(limit).toList();
        response.answerable = !response.results.isEmpty();
        response.noAnswerReason = response.answerable ? null : source.noAnswerReason;
        return response;
    }

    private RagDto.RetrievalResponse mergeRetrievals(
            RagDto.RetrievalResponse first,
            RagDto.RetrievalResponse second,
            int limit
    ) {
        Map<UUID, RagDto.RetrievedChunk> byChunkId = new LinkedHashMap<>();
        List<RagDto.RetrievedChunk> candidates = new ArrayList<>();
        if (first != null && first.results != null) candidates.addAll(first.results);
        if (second != null && second.results != null) candidates.addAll(second.results);
        candidates.sort(Comparator.comparingDouble(
                chunk -> chunk.similarityScore == null ? 0.0 : -chunk.similarityScore));
        for (RagDto.RetrievedChunk chunk : candidates) {
            if (chunk.chunkId != null) byChunkId.putIfAbsent(chunk.chunkId, chunk);
        }

        Set<String> seenContent = new LinkedHashSet<>();
        List<RagDto.RetrievedChunk> merged = byChunkId.values().stream()
                .filter(chunk -> {
                    String normalized = normalizeText(firstNonBlank(chunk.content, ""));
                    String signature = normalized.substring(0, Math.min(normalized.length(), 350));
                    return signature.isBlank() || seenContent.add(signature);
                })
                .limit(limit)
                .toList();

        RagDto.RetrievalResponse preferred = second != null && second.results != null
                && !second.results.isEmpty() ? second : first;
        RagDto.RetrievalResponse response = copyRetrievalMetadata(preferred);
        response.results = merged;
        response.answerable = !merged.isEmpty();
        response.noAnswerReason = response.answerable
                ? null
                : firstNonBlank(second == null ? null : second.noAnswerReason,
                        first == null ? null : first.noAnswerReason);
        return response;
    }

    private RagDto.RetrievalResponse copyRetrievalMetadata(RagDto.RetrievalResponse source) {
        RagDto.RetrievalResponse response = new RagDto.RetrievalResponse();
        if (source == null) {
            response.results = List.of();
            response.answerable = false;
            return response;
        }
        response.retrievalQueryId = source.retrievalQueryId;
        response.embeddingModelId = source.embeddingModelId;
        response.embeddingModelName = source.embeddingModelName;
        response.answerable = source.answerable;
        response.noAnswerReason = source.noAnswerReason;
        response.results = source.results == null ? List.of() : source.results;
        return response;
    }

    private List<PythonAiDto.ChatHistoryItem> recentHistory(UUID sessionId) {
        List<ChatMessage> newestFirst =
                chatMessageRepository.findTop12ByChatSessionIdOrderByCreatedAtDesc(sessionId);
        List<PythonAiDto.ChatHistoryItem> history = new ArrayList<>();
        for (int index = newestFirst.size() - 1; index >= 0; index--) {
            ChatMessage message = newestFirst.get(index);
            String role = message.getSenderRole();
            if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) continue;
            if (message.getMessageContent() == null || message.getMessageContent().isBlank()) continue;
            PythonAiDto.ChatHistoryItem item = new PythonAiDto.ChatHistoryItem();
            item.role = role.toLowerCase(java.util.Locale.ROOT);
            item.content = message.getMessageContent();
            history.add(item);
        }
        return history;
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

    private PythonAiDto.GenerateRequest toGenerateRequest(
            String question,
            String standaloneQuery,
            List<PythonAiDto.ChatHistoryItem> history,
            String answerProfile,
            List<RagDto.RetrievedChunk> chunks,
            boolean strict
    ) {
        PythonAiDto.GenerateRequest request = new PythonAiDto.GenerateRequest();
        request.question = question;
        request.strict = strict;
        request.standalone_query = standaloneQuery;
        request.history = history;
        request.answer_profile = answerProfile;
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
        context.score = chunk.similarityScore;
        return context;
    }

    private List<ChatDto.CitationItem> saveCitations(
            ChatMessage assistantMessage,
            List<RagDto.RetrievedChunk> retrievedChunks,
            List<Map<String, Object>> pythonSources) {
        return saveCitations(assistantMessage, retrievedChunks, pythonSources, List.of());
    }

    private List<ChatDto.CitationItem> saveCitations(
            ChatMessage assistantMessage,
            List<RagDto.RetrievedChunk> retrievedChunks,
            List<Map<String, Object>> pythonSources,
            List<String> usedChunkIds) {
        List<RagDto.RetrievedChunk> citedChunks = selectCitedChunks(retrievedChunks, pythonSources, usedChunkIds);
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

            ChatDto.CitationItem citationItem = new ChatDto.CitationItem(
                    citation.getCitationId(),
                    citation.getAssistantMessageId(),
                    citation.getRetrievalResultId(),
                    citation.getChunkId(),
                    citation.getDocumentId(),
                    citation.getDocumentTitle(),
                    citation.getPageStart(),
                    citation.getPageEnd(),
                    citation.getQuoteText()
            );
            citationItem.retrievalScore = chunk.similarityScore;
            citationItems.add(citationItem);
        }

        return citationItems;
    }

    private List<RagDto.RetrievedChunk> selectCitedChunks(
            List<RagDto.RetrievedChunk> retrievedChunks,
            List<Map<String, Object>> pythonSources,
            List<String> usedChunkIds) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) return List.of();

        List<UUID> sourceChunkIds = usedChunkIds == null ? new ArrayList<>() : usedChunkIds.stream()
                .map(this::parseUuid)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (sourceChunkIds.isEmpty() && pythonSources != null) {
            sourceChunkIds.addAll(pythonSources.stream()
                    .map(source -> parseUuid(source.get("chunk_id")))
                    .filter(java.util.Objects::nonNull)
                    .toList());
        }

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

    private void applyGenerationMetadata(ChatDto.AskResponse response, String providerUsed, String baseModel,
            String adapterVersion, String embeddingModel, String datasetVersion, String promptVersion,
            List<String> usedChunkIds, Long peakVramBytes) {
        response.providerUsed = providerUsed;
        response.baseModel = baseModel;
        response.adapterVersion = adapterVersion;
        response.embeddingModel = embeddingModel;
        response.datasetVersion = datasetVersion;
        response.promptVersion = promptVersion;
        response.usedChunkIds = usedChunkIds == null ? List.of() : List.copyOf(usedChunkIds);
        response.peakVramBytes = peakVramBytes;
    }

    private ChatDto.AskResponse completeResponse(ChatDto.AskResponse response, long startedAt) {
        int latencyMs = elapsedMs(startedAt);
        response.latencyMs = latencyMs;
        if (response.assistantMessageId != null) {
            chatMessageRepository.findById(response.assistantMessageId).ifPresent(message -> {
                message.setLatencyMs(latencyMs);
                chatMessageRepository.save(message);
            });
        }
        log.info("AI chat request completed in {} ms for session {}", latencyMs, response.chatSessionId);
        return response;
    }

    private int elapsedMs(long startedAt) {
        long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, elapsed));
    }

    String buildLocalFallbackAnswer(List<RagDto.RetrievedChunk> chunks) {
        List<RagDto.RetrievedChunk> usable = chunks == null ? List.of() : chunks.stream()
                .filter(chunk -> chunk != null && chunk.content != null && !chunk.content.isBlank())
                .limit(3)
                .toList();
        if (usable.isEmpty()) {
            return OUT_OF_SCOPE_MESSAGE;
        }

        StringBuilder answer = new StringBuilder();
        answer.append("### Thông tin tạm thời từ tài liệu\n\n")
                .append("Dịch vụ tạo câu trả lời đang tạm thời chưa sẵn sàng. ")
                .append("Dưới đây là các đoạn liên quan nhất đã được hệ thống tìm thấy:\n\n");
        for (int index = 0; index < usable.size(); index++) {
            answer.append("- ")
                    .append(cleanFallbackText(usable.get(index).content))
                    .append("\n");
        }
        answer.append("\n> Đây là nội dung trích xuất, chưa phải câu trả lời đã được AI tổng hợp. ")
                .append("Vui lòng đối chiếu các nguồn bên dưới.");
        return answer.toString().trim();
    }

    private List<String> fallbackChunkIds(List<RagDto.RetrievedChunk> chunks) {
        if (chunks == null) return List.of();
        return chunks.stream()
                .filter(chunk -> chunk != null && chunk.chunkId != null)
                .limit(3)
                .map(chunk -> chunk.chunkId.toString())
                .toList();
    }

    private String cleanFallbackText(String content) {
        String cleaned = content == null ? "" : content
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() <= 360) return cleaned;
        int sentenceEnd = Math.max(cleaned.lastIndexOf(". ", 360), cleaned.lastIndexOf("。", 360));
        if (sentenceEnd >= 180) return cleaned.substring(0, sentenceEnd + 1).trim();
        return cleaned.substring(0, 360).trim() + "...";
    }

    private String classifyAiFailure(Exception exception) {
        String message = exception == null || exception.getMessage() == null
                ? ""
                : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("connect")) return "PYTHON_AI_UNAVAILABLE";
        if (message.contains("timeout") || message.contains("timed out")) return "PYTHON_AI_TIMEOUT";
        if (message.contains("model") || message.contains("adapter")) return "MODEL_NOT_READY";
        return "PYTHON_GENERATION_FAILED";
    }

    String formatAnswerForDisplay(String answer, String answerProfile) {
        String cleaned = answer == null ? "" : answer.trim();
        if (cleaned.isBlank()) {
            return cleaned;
        }
        String firstLine = cleaned.split("\\R", 2)[0].trim();
        if (Pattern.compile("(?m)^\\s*(?:[-*+]\\s+|\\|.+\\|\\s*$)")
                .matcher(cleaned).find()
                || firstLine.matches("^\\d+[.)]\\s+.*")) {
            return cleaned;
        }

        String expanded = cleaned.replaceAll("\\s+(?=\\d+[.)]\\s+)", "\n");
        List<String> units = new ArrayList<>();
        for (String line : expanded.split("\\R+")) {
            String withoutMarker = line.replaceFirst("^\\s*\\d+[.)]\\s+", "").trim();
            for (String sentence : withoutMarker.split("(?<=[.!?;])\\s+")) {
                String unit = sentence.trim();
                if (unit.length() >= 8) units.add(unit);
            }
        }
        if (units.size() < 2) return cleaned;

        String profile = answerProfile == null
                ? "factual"
                : answerProfile.toLowerCase(java.util.Locale.ROOT);
        if ("reasoning".equals(profile)) {
            StringBuilder formatted = new StringBuilder()
                    .append("**Trả lời trực tiếp:** ").append(units.get(0))
                    .append("\n\n**Các lý do chính:**\n");
            units.stream().skip(1).limit(4)
                    .forEach(item -> formatted.append("- ").append(item).append("\n"));
            return formatted.append("\n**Kết luận:** ").append(units.get(0)).toString().trim();
        }
        if ("definition".equals(profile)) {
            StringBuilder formatted = new StringBuilder()
                    .append("**Định nghĩa:** ").append(units.get(0))
                    .append("\n\n**Đặc điểm chính:**\n");
            units.stream().skip(1).limit(4)
                    .forEach(item -> formatted.append("- ").append(item).append("\n"));
            return formatted.toString().trim();
        }
        if ("procedure".equals(profile)) {
            StringBuilder formatted = new StringBuilder();
            for (int index = 0; index < Math.min(units.size(), 7); index++) {
                formatted.append(index + 1).append(". ").append(units.get(index)).append("\n");
            }
            return formatted.toString().trim();
        }
        if (Set.of("list", "summary", "comparison").contains(profile)) {
            StringBuilder formatted = new StringBuilder();
            units.stream().limit(8)
                    .forEach(item -> formatted.append("- ").append(item).append("\n"));
            return formatted.toString().trim();
        }
        if ("factual".equals(profile) && units.size() >= 3) {
            StringBuilder formatted = new StringBuilder(units.get(0)).append("\n\n");
            units.stream().skip(1).limit(4)
                    .forEach(item -> formatted.append("- ").append(item).append("\n"));
            return formatted.toString().trim();
        }
        return cleaned;
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
                .isPinned(Boolean.TRUE.equals(session.getIsPinned()))
                .pinnedAt(session.getPinnedAt())
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
