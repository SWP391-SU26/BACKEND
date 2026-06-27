package com.courseqa.service;
//----------------------------------
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//----------------------------------
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
//-----------------------------------
import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.dto.PythonAiDto;
import com.courseqa.model.entity.AnswerCitation;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.AnswerCitationRepository;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
//-------------------------------------
@Service
public class ChatService {

private static final Logger log = LoggerFactory.getLogger(ChatService.class);

private final ChatSessionRepository chatSessionRepository;
private final ChatMessageRepository chatMessageRepository;
private final CourseWorkspaceRepository courseWorkspaceRepository;
private final AIClientService aiClientService;
private final AnswerCitationRepository answerCitationRepository;

public ChatService(
        ChatSessionRepository chatSessionRepository,
        ChatMessageRepository chatMessageRepository,
        CourseWorkspaceRepository courseWorkspaceRepository,
        AIClientService aiClientService,
        AnswerCitationRepository answerCitationRepository) {
    this.chatSessionRepository = chatSessionRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.courseWorkspaceRepository = courseWorkspaceRepository;
    this.aiClientService = aiClientService;
    this.answerCitationRepository = answerCitationRepository;
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

        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"));
        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId, pageable)
                .getContent();

        log.debug("Found {} messages in history for sessionId: {}", messages.size(), sessionId);
        return messages;
    }

/*    public void askQuestion(UUID sessionId, String question) {
        log.info("TODO: Implement askQuestion for sessionId: {}, question: {}", sessionId, question);
        throw new UnsupportedOperationException("askQuestion not implemented yet - waiting for Python API contract from TV6");
    }*/

// ── new implementation ─────────────────────────────────────────────────────

    public ChatDto.AskResponse askQuestion(UUID sessionId, String question) {
        log.info("askQuestion - sessionId: {}, question: {}", sessionId, question);

        // 1. Validate session exists
        chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found: " + sessionId));

        // 2. Save user message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setChatSessionId(sessionId);
        userMessage.setSenderRole("user");
        userMessage.setMessageContent(question);
        userMessage.setCreatedAt(LocalDateTime.now());
        ChatMessage savedUserMessage = chatMessageRepository.save(userMessage);

       
       // 3. Call Python — guarded so a failure doesn't strand the user message
PythonAiDto.ChatResponse pyResponse;
try {
    PythonAiDto.ChatRequest pyRequest = new PythonAiDto.ChatRequest();
    pyRequest.question = question;
    pyRequest.session_id = null;  // let Python manage its own session
    pyRequest.subject = null;     // no subject filter for now

    pyResponse = aiClientService.callChat(
            pyRequest, PythonAiDto.ChatResponse.class);
} catch (Exception e) {
    log.error("Python AI call failed for sessionId {}: {}", sessionId, e.getMessage());

    // Save a fallback assistant message so the conversation isn't left lopsided
    ChatMessage errorMessage = new ChatMessage();
    errorMessage.setChatSessionId(sessionId);
    errorMessage.setSenderRole("assistant");
    errorMessage.setMessageContent("Xin lỗi, hệ thống AI hiện không phản hồi. Vui lòng thử lại sau.");
    errorMessage.setCreatedAt(LocalDateTime.now());
    ChatMessage savedErrorMessage = chatMessageRepository.save(errorMessage);

    return new ChatDto.AskResponse(
            sessionId,
            savedUserMessage.getMessageId(),
            savedErrorMessage.getMessageId(),
            errorMessage.getMessageContent(),
            new ArrayList<>()   // no citations
    );
}
        // 4. Save assistant message
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setChatSessionId(sessionId);
        assistantMessage.setSenderRole("assistant");
        assistantMessage.setMessageContent(pyResponse.answer);
        assistantMessage.setCreatedAt(LocalDateTime.now());
        ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantMessage);

       
     // 5. Save citations from sources
List<ChatDto.CitationItem> citationItems = new ArrayList<>();
if (pyResponse.sources != null) {
    for (int i = 0; i < pyResponse.sources.size(); i++) {
        Map<String, Object> source = pyResponse.sources.get(i);

        Integer page = source.get("page") != null ? (Integer) source.get("page") : null;

        AnswerCitation citation = new AnswerCitation();
        citation.setAssistantMessageId(savedAssistantMessage.getMessageId());
        citation.setCitationOrder(i + 1);

        // ── link IDs returned by Python (best-effort) ──
        citation.setDocumentId(parseUuid(source.get("document_id")));
        citation.setChunkId(parseUuid(source.get("chunk_id")));

        // ── descriptive fields ──
        citation.setDocumentTitle((String) source.get("filename"));
        citation.setPageStart(page);
        citation.setPageEnd(page);   // Python returns a single page number
        citation.setQuoteText((String) source.get("preview"));
        citation.setCreatedAt(LocalDateTime.now());
        answerCitationRepository.save(citation);

        citationItems.add(new ChatDto.CitationItem(
                (String) source.get("filename"),
                page,
                page,
                (String) source.get("preview")
        ));
    }
}

        // 6. Return response
        return new ChatDto.AskResponse(
                sessionId,
                savedUserMessage.getMessageId(),
                savedAssistantMessage.getMessageId(),
                pyResponse.answer,
                citationItems
        );
    }

//helper method 
/**
 * Safely parse a value into a UUID. Python may send IDs as strings that
 * are not valid Java UUIDs (its SQLite uses its own ID format), so we
 * return null rather than crash when the value can't be parsed.
 */
private UUID parseUuid(Object value) {
    if (value == null) {
        return null;
    }
    try {
        return UUID.fromString(value.toString());
    } catch (IllegalArgumentException e) {
        log.debug("Could not parse '{}' as UUID for citation link", value);
        return null;
    }
}



}
