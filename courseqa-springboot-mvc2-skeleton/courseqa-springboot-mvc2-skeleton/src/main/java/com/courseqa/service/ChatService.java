package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.SavedNote;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.SavedNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ChatService - Quản lý chat sessions, messages, notes
 * Phần SQL implementation, TODO: gọi Python AI Engine sau khi có API contract từ TV6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SavedNoteRepository savedNoteRepository;

    /**
     * Tạo hoặc lấy session hiện tại
     * - Tìm session active đã có
     * - Nếu không có hoặc session không active, tạo mới
     *
     * @param userId ID của user
     * @param workspaceId ID của workspace
     * @return ChatSession (mới hoặc đã có)
     */
    public ChatSession createOrGetSession(Long userId, Long workspaceId) {
        logger.info("Creating or getting chat session for userId: {}, workspaceId: {}", userId, workspaceId);

        // Tìm session active đã có
        Optional<ChatSession> existingSession = chatSessionRepository
                .findByUserIdAndWorkspaceIdAndIsActiveTrue(userId, workspaceId);

        if (existingSession.isPresent()) {
            logger.debug("Found existing active session: {}", existingSession.get().getId());
            return existingSession.get();
        }

        // Tạo session mới
        ChatSession newSession = ChatSession.builder()
                .userId(userId)
                .workspaceId(workspaceId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        ChatSession savedSession = chatSessionRepository.save(newSession);
        logger.info("Created new chat session: {}", savedSession.getId());
        return savedSession;
    }

    /**
     * Lấy lịch sử chat
     * - Tối đa 50 messages gần nhất
     * - Sort theo created_at ASC (từ cũ đến mới)
     *
     * @param sessionId ID của session
     * @return List<ChatMessage>
     */
    public List<ChatMessage> getHistory(Long sessionId) {
        logger.info("Fetching chat history for sessionId: {}", sessionId);

        // Kiểm tra session tồn tại
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", "id", sessionId));

        // Lấy 50 messages gần nhất, sort theo created_at ASC
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"));
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable)
                .getContent();

        logger.debug("Found {} messages in history for sessionId: {}", messages.size(), sessionId);
        return messages;
    }

    /**
     * Lưu note người dùng
     *
     * @param userId ID của user
     * @param workspaceId ID của workspace
     * @param content Nội dung note
     * @return SavedNote
     */
    public SavedNote saveNote(Long userId, Long workspaceId, String content) {
        logger.info("Saving note for userId: {}, workspaceId: {}", userId, workspaceId);

        SavedNote note = SavedNote.builder()
                .userId(userId)
                .workspaceId(workspaceId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        SavedNote savedNote = savedNoteRepository.save(note);
        logger.info("Saved note with id: {}", savedNote.getId());
        return savedNote;
    }

    /**
     * Lấy danh sách notes của workspace
     *
     * @param workspaceId ID của workspace
     * @return List<SavedNote>
     */
    public List<SavedNote> getNotes(Long workspaceId) {
        logger.info("Fetching notes for workspaceId: {}", workspaceId);

        List<SavedNote> notes = savedNoteRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        logger.debug("Found {} notes for workspaceId: {}", notes.size(), workspaceId);
        return notes;
    }

    /**
     * TODO: Hỏi câu hỏi (gọi Python AI Engine)
     * Cần implement khi có API contract từ TV6
     *
     * Logic:
     * 1. Lấy session + 10 turns lịch sử gần nhất (role, content)
     * 2. Gọi song parallel:
     *    - AIClientService.callChat() → RAG answer
     *    - AIClientService.callChatFinetuned() → Finetuned answer
     * 3. Gọi AIClientService.callEvaluate() → winner
     * 4. Lưu ChatMessage (role=user/assistant, rag_answer, finetuned_answer, winner)
     * 5. Lưu AnswerCitation nếu có citations
     * 6. Trả về response cho FE
     */
    public void askQuestion(Long sessionId, String question) {
        logger.info("TODO: Implement askQuestion for sessionId: {}, question: {}", sessionId, question);
        throw new UnsupportedOperationException("askQuestion not implemented yet - waiting for Python API contract from TV6");
    }
}
