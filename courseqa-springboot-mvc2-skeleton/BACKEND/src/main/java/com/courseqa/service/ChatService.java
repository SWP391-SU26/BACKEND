package com.courseqa.service;

import com.courseqa.exception.ResourceNotFoundException;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.CourseWorkspace;
import com.courseqa.model.entity.SavedNote;
import com.courseqa.repository.ChatMessageRepository;
import com.courseqa.repository.ChatSessionRepository;
import com.courseqa.repository.CourseWorkspaceRepository;
import com.courseqa.repository.SavedNoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ChatService - Quản lý chat sessions, messages, notes
 * Phần SQL implementation, TODO: gọi Python AI Engine sau khi có API contract từ TV6
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SavedNoteRepository savedNoteRepository;
    private final CourseWorkspaceRepository courseWorkspaceRepository;

    public ChatService(ChatSessionRepository chatSessionRepository, ChatMessageRepository chatMessageRepository, SavedNoteRepository savedNoteRepository, CourseWorkspaceRepository courseWorkspaceRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.savedNoteRepository = savedNoteRepository;
        this.courseWorkspaceRepository = courseWorkspaceRepository;
    }

    /**
     * Tạo hoặc lấy session hiện tại
     * - Tìm session active đã có
     * - Nếu không có hoặc session không active, tạo mới
     *
     * @param userId ID của user
     * @param workspaceId ID của workspace
     * @return ChatSession (mới hoặc đã có)
     */
    public ChatSession createOrGetSession(UUID userId, UUID workspaceId) {
        log.info("Creating or getting chat session for userId: {}, workspaceId: {}", userId, workspaceId);

        ChatSession existingSession = chatSessionRepository.findByUserIdAndWorkspaceIdAndIsActiveTrue(userId, workspaceId)
                .orElse(null);
        if (existingSession != null) {
            log.info("Found existing active chat session: {}", existingSession.getChatSessionId());
            return existingSession;
        }

        CourseWorkspace workspace = courseWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseWorkspace not found with id: " + workspaceId));

        // Tạo session mới
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

    /**
     * Lấy lịch sử chat
     * - Tối đa 50 messages gần nhất
     * - Sort theo created_at ASC (từ cũ đến mới)
     *
     * @param sessionId ID của session
     * @return List<ChatMessage>
     */
    public List<ChatMessage> getHistory(UUID sessionId) {
        log.info("Fetching chat history for sessionId: {}", sessionId);

        // Kiểm tra session tồn tại
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession not found with id: " + sessionId));

        // Lấy 50 messages gần nhất, sort theo created_at ASC
        Pageable pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"));
        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId, pageable)
                .getContent();

        log.debug("Found {} messages in history for sessionId: {}", messages.size(), sessionId);
        return messages;
    }

    /**
     * Lưu note người dùng
     *
     * @param userId ID của user
     * @param workspaceId ID của workspace
     * @param noteTitle Tiêu đề note
     * @param noteContent Nội dung note
     * @return SavedNote
     */
    public SavedNote saveNote(UUID userId, UUID workspaceId, String noteTitle, String noteContent) {
        log.info("Saving note for userId: {}, workspaceId: {}", userId, workspaceId);

        SavedNote note = new SavedNote();
        note.setUserId(userId);
        note.setWorkspaceId(workspaceId);
        note.setNoteTitle(noteTitle);
        note.setNoteContent(noteContent);
        note.setNoteType("MANUAL");
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        SavedNote savedNote = savedNoteRepository.save(note);
        log.info("Saved note with id: {}", savedNote.getNoteId());
        return savedNote;
    }

    /**
     * Lấy danh sách notes của workspace
     *
     * @param workspaceId ID của workspace
     * @return List<SavedNote>
     */
    public List<SavedNote> getNotes(UUID workspaceId) {
        log.info("Fetching notes for workspaceId: {}", workspaceId);

        List<SavedNote> notes = savedNoteRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        log.debug("Found {} notes for workspaceId: {}", notes.size(), workspaceId);
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
    public void askQuestion(UUID sessionId, String question) {
        log.info("TODO: Implement askQuestion for sessionId: {}, question: {}", sessionId, question);
        throw new UnsupportedOperationException("askQuestion not implemented yet - waiting for Python API contract from TV6");
    }
}
