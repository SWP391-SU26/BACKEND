package com.courseqa.controller;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.courseqa.security.JwtPrincipal;

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
    public ResponseEntity<ApiResponse<ChatDto.SessionResponse>> createSession(@AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody CreateSessionRequest request) {
        log.info("POST /api/chat/sessions - userId: {}, courseId: {}", principal.userId(), request.getCourseId());
        ChatSession session = chatService.createSession(principal.userId(), request.getScopeType(),
                request.getSemesterId(), request.getCourseId(), request.getDocumentIds(),
                principal.roles().contains("ADMIN"), request.getTitle());

        return ResponseEntity.ok(ApiResponse.ok(chatService.toSessionResponse(session)));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatDto.SessionResponse>> getSessions(
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String scopeType,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(chatService.getSessions(principal.userId(), semesterId, courseId, scopeType,
                principal.roles().contains("ADMIN")));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable UUID sessionId, @AuthenticationPrincipal JwtPrincipal principal) {
        chatService.deleteSession(sessionId, principal.userId()); return ApiResponse.ok(null);
    }

    @PatchMapping("/sessions/{sessionId}/pin")
    public ApiResponse<ChatDto.SessionResponse> pinSession(
            @PathVariable UUID sessionId,
            @RequestBody ChatDto.PinRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(chatService.pinSession(sessionId, principal.userId(), request.isPinned()));
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
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody AskQuestionRequest request) {
        log.info("POST /api/chat/sessions/{}/ask - question: {}", sessionId, request.getQuestion());

        // TODO: Implement askQuestion logic:
        // 1. Gọi chatService.askQuestion(sessionId, question)
        // 2. TODO sẽ implement khi TV6 confirm API contract
    chatService.requireSessionOwner(sessionId, principal.userId());
    ChatDto.AskResponse response = chatService.askQuestion(sessionId, request.getQuestion(), request.getAnswerMode());

    return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping(value = "/sessions/{sessionId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askQuestionStream(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody AskQuestionRequest request) {
        chatService.requireSessionOwner(sessionId, principal.userId());
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            long startedAt = System.currentTimeMillis();
            try {
                ChatDto.AskResponse response = chatService.askQuestion(
                        sessionId, request.getQuestion(), "RAG", false,
                        stage -> sendEvent(emitter, stage, Map.of(
                                "stage", stage,
                                "elapsedMs", System.currentTimeMillis() - startedAt)));
                sendEvent(emitter, "COMPLETED", Map.of(
                        "stage", "COMPLETED",
                        "elapsedMs", System.currentTimeMillis() - startedAt,
                        "response", response));
                emitter.complete();
            } catch (RuntimeException exception) {
                sendEvent(emitter, "ERROR", Map.of(
                        "stage", "ERROR",
                        "message", exception.getMessage() == null ? "Chat processing failed." : exception.getMessage(),
                        "elapsedMs", System.currentTimeMillis() - startedAt));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Chat stream was disconnected.", exception);
        }
    }

    /**
     * GET /api/chat/sessions/{sessionId}/history
     * Lấy lịch sử chat (50 messages gần nhất)
     *
     * @param sessionId ID của session
     * @return ResponseEntity<ApiResponse<List<ChatMessage>>>
     */
    @GetMapping("/sessions/{sessionId}/history")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getHistory(@PathVariable UUID sessionId, @AuthenticationPrincipal JwtPrincipal principal) {
        log.info("GET /api/chat/sessions/{}/history", sessionId);

        chatService.requireSessionOwner(sessionId, principal.userId());
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
    public ResponseEntity<ApiResponse<SavedNote>> saveNote(@AuthenticationPrincipal JwtPrincipal principal, @Valid @RequestBody SaveNoteRequest request) {
        log.info("POST /api/chat/notes - userId: {}, workspaceId: {}", principal.userId(), request.getWorkspaceId());

        SavedNote note = noteService.saveNote(principal.userId(), request.getWorkspaceId(), request.getNoteTitle(), request.getNoteContent());

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
    public ResponseEntity<ApiResponse<List<SavedNote>>> getNotes(@PathVariable UUID workspaceId, @AuthenticationPrincipal JwtPrincipal principal) {
        log.info("GET /api/chat/notes/workspace/{}", workspaceId);

        List<SavedNote> notes = noteService.getNotes(workspaceId, principal.userId());

        return ResponseEntity.ok(ApiResponse.ok(notes));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createOrGetSession
     */
    public static class CreateSessionRequest {
        private String scopeType;
        private UUID semesterId;
        private UUID courseId;
        private List<UUID> documentIds = List.of();
        private String title;

        public CreateSessionRequest() {}

        public String getScopeType() { return scopeType; }
        public void setScopeType(String scopeType) { this.scopeType = scopeType; }
        public UUID getSemesterId() { return semesterId; }
        public void setSemesterId(UUID semesterId) { this.semesterId = semesterId; }
        public UUID getCourseId() { return courseId; }
        public void setCourseId(UUID courseId) { this.courseId = courseId; }
        public List<UUID> getDocumentIds() { return documentIds; }
        public void setDocumentIds(List<UUID> documentIds) { this.documentIds = documentIds == null ? List.of() : documentIds; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    /**
     * Request DTO cho askQuestion
     */
    public static class AskQuestionRequest {
        @NotBlank(message = "question is required and cannot be empty")
        private String question;
        private String answerMode;

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

        public String getAnswerMode() {
            return answerMode;
        }

        public void setAnswerMode(String answerMode) {
            this.answerMode = answerMode;
        }
    }

    /**
     * Request DTO cho saveNote
     */
    public static class SaveNoteRequest {
        @NotNull(message = "workspaceId is required")
        private UUID workspaceId;

        @NotBlank(message = "noteTitle is required and cannot be empty")
        private String noteTitle;

        @NotBlank(message = "noteContent is required and cannot be empty")
        private String noteContent;

        public SaveNoteRequest() {}

        public SaveNoteRequest(UUID workspaceId, String noteTitle, String noteContent) {
            this.workspaceId = workspaceId;
            this.noteTitle = noteTitle;
            this.noteContent = noteContent;
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
