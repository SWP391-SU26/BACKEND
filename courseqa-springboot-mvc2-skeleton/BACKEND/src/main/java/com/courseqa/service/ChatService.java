package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.dto.RagDto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            "Xin lỗi, mình không tìm thấy nội dung liên quan trong tài liệu của workspace này.";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;
    private final AIClientService aiClientService;
    private final AnswerCitationRepository answerCitationRepository;
    private final RetrievalService retrievalService;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            CourseWorkspaceRepository courseWorkspaceRepository,
            AIClientService aiClientService,
            AnswerCitationRepository answerCitationRepository,
            RetrievalService retrievalService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
        this.aiClientService = aiClientService;
        this.answerCitationRepository = answerCitationRepository;
        this.retrievalService = retrievalService;
    }

    public ChatSession createOrGetSession(UUID userId, UUID workspaceId) {
        log.info("Creating or getting chat session for userId: {}, workspaceId: {}", userId, workspaceId);

        ChatSession existingSession = chatSessionRepository
                .findByUserIdAndWorkspaceIdAndIsActiveTrue(userId, workspaceId)
                .orElse(null);
        if (existingSession != null) {
            log.info("Found existing active chat session: {}", existingSession.getChatSessionId());
            return existingSession;
        }

        CourseWorkspace workspace = courseWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseWorkspace not found with id: " + workspaceId));

        ChatSession newSession = new ChatSession();
        newSession.setUserId(userId);
        newSession.setWorkspaceId(workspaceId);
        newSession.setCourseId(workspace.getCourseId());
        newSession.setIsActive(true);
        newSession.setStartedAt(LocalDateTime.now());
        newSession.setUpdatedAt(LocalDateTime.now());

        ChatSession savedSession = chatSessionRepository.save(newSession);
        log.info("Created new chat session: {}", savedSession.getChatSessionId());
        return savedSession;
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

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question) {
        log.info("askQuestion - sessionId: {}, question: {}", sessionId, question);

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found: " + sessionId));

        ChatMessage savedUserMessage = saveMessage(sessionId, "user", question);

        RagDto.RetrievalResponse retrieval = retrieveFromJavaSql(session, savedUserMessage, question);

        // Out-of-scope gate: need at least one chunk AND a strong enough top match.
        double topScore = (retrieval.results == null || retrieval.results.isEmpty()
                || retrieval.results.get(0).similarityScore == null)
                ? 0.0
                : retrieval.results.get(0).similarityScore;

        if (retrieval.results == null || retrieval.results.isEmpty() || topScore < 0.25) {
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", OUT_OF_SCOPE_MESSAGE);
            return new ChatDto.AskResponse(
                    sessionId,
                    savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(),
                    OUT_OF_SCOPE_MESSAGE,
                    new ArrayList<>()
            );
        }

        PythonAiDto.GenerateResponse generated;
        try {
            generated = aiClientService.callGenerate(
                    toGenerateRequest(question, retrieval.results),
                    PythonAiDto.GenerateResponse.class
            );
        } catch (Exception exception) {
            log.error("Python /api/generate failed for sessionId {}: {}", sessionId, exception.getMessage());
            String message = "Xin lỗi, hệ thống AI hiện không phản hồi. Vui lòng thử lại sau.";
            ChatMessage assistantMessage = saveMessage(sessionId, "assistant", message);
            return new ChatDto.AskResponse(
                    sessionId,
                    savedUserMessage.getMessageId(),
                    assistantMessage.getMessageId(),
                    message,
                    new ArrayList<>()
            );
        }

        String answer = Boolean.TRUE.equals(generated.is_out_of_scope)
                ? OUT_OF_SCOPE_MESSAGE
                : (generated.answer == null || generated.answer.isBlank() ? OUT_OF_SCOPE_MESSAGE : generated.answer);

        ChatMessage savedAssistantMessage = saveMessage(sessionId, "assistant", answer);
        List<ChatDto.CitationItem> citations = saveCitations(savedAssistantMessage, retrieval.results, generated.sources);

        return new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                savedAssistantMessage.getMessageId(),
                answer,
                citations
        );
    }

    private RagDto.RetrievalResponse retrieveFromJavaSql(ChatSession session, ChatMessage userMessage, String question) {
        RagDto.RetrievalRequest request = new RagDto.RetrievalRequest();
        request.chatSessionId = session.getChatSessionId();
        request.userMessageId = userMessage.getMessageId();
        request.workspaceId = session.getWorkspaceId();
        request.queryText = question;
        request.embeddingModelId = session.getSelectedEmbeddingModelId();
        request.topK = 5;
        request.similarityThreshold = 0.05;
        return retrievalService.retrieve(request);
    }

    private PythonAiDto.GenerateRequest toGenerateRequest(String question, List<RagDto.RetrievedChunk> chunks) {
        PythonAiDto.GenerateRequest request = new PythonAiDto.GenerateRequest();
        request.question = question;
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
            return retrievedChunks.stream().limit(3).toList();
        }

        List<UUID> sourceChunkIds = pythonSources.stream()
                .map(source -> parseUuid(source.get("chunk_id")))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (sourceChunkIds.isEmpty()) {
            return retrievedChunks.stream().limit(3).toList();
        }

        return retrievedChunks.stream()
                .filter(chunk -> sourceChunkIds.contains(chunk.chunkId))
                .limit(3)
                .toList();
    }

    private ChatMessage saveMessage(UUID sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setChatSessionId(sessionId);
        message.setSenderRole(role);
        message.setMessageContent(content);
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
}
