package com.courseqa.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.ChatDto;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.SavedNote;
import com.courseqa.service.ChatService;
import com.courseqa.service.NoteService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * ChatController - API endpoints cho chat functionality
 * - Tạo/lấy session
 * - Hỏi câu hỏi (TODO: gọi Python)
 * - Lấy lịch sử chat
 * - Lưu/lấy notes
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final NoteService noteService;

    public ChatController(ChatService chatService, NoteService noteService) {
        this.chatService = chatService;
        this.noteService = noteService;
    }

    /**
     * POST /api/chat/sessions
     * Tạo hoặc lấy session hiện tại
     *
     * @param request CreateSessionRequest: {userId, workspaceId}
     * @return ResponseEntity<ApiResponse<ChatSession>>
     */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSession>> createOrGetSession(@Valid @RequestBody CreateSessionRequest request) {
        log.info("POST /api/chat/sessions - userId: {}, workspaceId: {}", request.getUserId(), request.getWorkspaceId());

        ChatSession session = chatService.createOrGetSession(request.getUserId(), request.getWorkspaceId());

        return ResponseEntity.ok(ApiResponse.ok(session));
    }

    /**
     * POST /api/chat/sessions/{sessionId}/ask
     * Hỏi câu hỏi (gọi Python AI Engine)
     * TODO: Implement sau khi có API contract từ TV6
     *
     * @param sessionId ID của session
     * @param request AskQuestionRequest: {question}
     * @return ResponseEntity<ApiResponse<ChatMessage>>
     */
    @PostMapping("/sessions/{sessionId}/ask")
    public ResponseEntity<ApiResponse<ChatDto.AskResponse>> askQuestion(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AskQuestionRequest request) {
        log.info("POST /api/chat/sessions/{}/ask - question: {}", sessionId, request.getQuestion());

        // TODO: Implement askQuestion logic:
        // 1. Gọi chatService.askQuestion(sessionId, question)
        // 2. TODO sẽ implement khi TV6 confirm API contract
    ChatDto.AskResponse response = chatService.askQuestion(sessionId, request.getQuestion());

    return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/chat/sessions/{sessionId}/history
     * Lấy lịch sử chat (50 messages gần nhất)
     *
     * @param sessionId ID của session
     * @return ResponseEntity<ApiResponse<List<ChatMessage>>>
     */
    @GetMapping("/sessions/{sessionId}/history")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getHistory(@PathVariable UUID sessionId) {
        log.info("GET /api/chat/sessions/{}/history", sessionId);

        List<ChatMessage> history = chatService.getHistory(sessionId);

        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    /**
     * POST /api/chat/notes
     * Lưu note từ user
     *
     * @param request SaveNoteRequest: {userId, workspaceId, content}
     * @return ResponseEntity<ApiResponse<SavedNote>>
     */
    @PostMapping("/notes")
    public ResponseEntity<ApiResponse<SavedNote>> saveNote(@Valid @RequestBody SaveNoteRequest request) {
        log.info("POST /api/chat/notes - userId: {}, workspaceId: {}", request.getUserId(), request.getWorkspaceId());

        SavedNote note = noteService.saveNote(request.getUserId(), request.getWorkspaceId(), request.getNoteTitle(), request.getNoteContent());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(note));
    }

    /**
     * GET /api/chat/notes/workspace/{workspaceId}
     * Lấy danh sách notes của workspace
     *
     * @param workspaceId ID của workspace
     * @return ResponseEntity<ApiResponse<List<SavedNote>>>
     */
    @GetMapping("/notes/workspace/{workspaceId}")
    public ResponseEntity<ApiResponse<List<SavedNote>>> getNotes(@PathVariable UUID workspaceId) {
        log.info("GET /api/chat/notes/workspace/{}", workspaceId);

        List<SavedNote> notes = noteService.getNotes(workspaceId);

        return ResponseEntity.ok(ApiResponse.ok(notes));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createOrGetSession
     */
    public static class CreateSessionRequest {
        @NotNull(message = "userId is required")
        private UUID userId;

        @NotNull(message = "workspaceId is required")
        private UUID workspaceId;

        public CreateSessionRequest() {}

        public CreateSessionRequest(UUID userId, UUID workspaceId) {
            this.userId = userId;
            this.workspaceId = workspaceId;
        }

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(UUID workspaceId) {
            this.workspaceId = workspaceId;
        }
    }

    /**
     * Request DTO cho askQuestion
     */
    public static class AskQuestionRequest {
        @NotBlank(message = "question is required and cannot be empty")
        private String question;

        public AskQuestionRequest() {}

        public AskQuestionRequest(String question) {
            this.question = question;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }

    /**
     * Request DTO cho saveNote
     */
    public static class SaveNoteRequest {
        @NotNull(message = "userId is required")
        private UUID userId;

        @NotNull(message = "workspaceId is required")
        private UUID workspaceId;

        @NotBlank(message = "noteTitle is required and cannot be empty")
        private String noteTitle;

        @NotBlank(message = "noteContent is required and cannot be empty")
        private String noteContent;

        public SaveNoteRequest() {}

        public SaveNoteRequest(UUID userId, UUID workspaceId, String noteTitle, String noteContent) {
            this.userId = userId;
            this.workspaceId = workspaceId;
            this.noteTitle = noteTitle;
            this.noteContent = noteContent;
        }

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public UUID getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(UUID workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getNoteTitle() {
            return noteTitle;
        }

        public void setNoteTitle(String noteTitle) {
            this.noteTitle = noteTitle;
        }

        public String getNoteContent() {
            return noteContent;
        }

        public void setNoteContent(String noteContent) {
            this.noteContent = noteContent;
        }
    }
}
