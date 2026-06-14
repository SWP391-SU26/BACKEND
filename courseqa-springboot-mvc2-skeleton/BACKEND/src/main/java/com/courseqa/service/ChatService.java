package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            CourseWorkspaceRepository courseWorkspaceRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
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

    public void askQuestion(UUID sessionId, String question) {
        log.info("TODO: Implement askQuestion for sessionId: {}, question: {}", sessionId, question);
        throw new UnsupportedOperationException("askQuestion not implemented yet - waiting for Python API contract from TV6");
    }
}
