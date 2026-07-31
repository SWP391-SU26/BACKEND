package com.courseqa.controller;

import java.util.List;
import java.util.UUID;

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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;

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
    private final Executor chatTaskExecutor;

    public ChatController(
            ChatService chatService,
            NoteService noteService,
            @Qualifier("chatTaskExecutor") Executor chatTaskExecutor
    ) {
        this.chatService = chatService;
        this.noteService = noteService;
        this.chatTaskExecutor = chatTaskExecutor;
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
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(chatService.getSessions(principal.userId(), semesterId, courseId, scopeType, query,
                principal.roles().contains("ADMIN")));
    }

    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<ChatDto.SessionResponse> renameSession(
            @PathVariable UUID sessionId,
            @RequestBody ChatDto.RenameSessionRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(chatService.renameSession(sessionId, principal.userId(),
                request == null ? null : request.getTitle()));
    }

    @PatchMapping("/sessions/{sessionId}/pin")
    public ApiResponse<ChatDto.SessionResponse> pinSession(
            @PathVariable UUID sessionId,
            @RequestBody ChatDto.PinSessionRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.ok(chatService.pinSession(sessionId, principal.userId(),
                request == null ? null : request.getPinned()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable UUID sessionId, @AuthenticationPrincipal JwtPrincipal principal) {
        chatService.deleteSession(sessionId, principal.userId()); return ApiResponse.ok(null);
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
    public SseEmitter streamQuestion(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody AskQuestionRequest request) {
        chatService.requireSessionOwner(sessionId, principal.userId());
        SseEmitter emitter = new SseEmitter(125_000L);
        AtomicBoolean terminal = new AtomicBoolean(false);
        long startedAt = System.nanoTime();

        CompletableFuture<ChatDto.AskResponse> task = CompletableFuture.supplyAsync(() ->
                chatService.askQuestion(
                        sessionId,
                        request.getQuestion(),
                        request.getAnswerMode(),
                        "FINE_TUNED".equalsIgnoreCase(request.getAnswerMode()),
                        phase -> sendPhase(emitter, terminal, phase, startedAt)
                ), CompletableFuture.delayedExecutor(25, TimeUnit.MILLISECONDS, chatTaskExecutor));

        task.whenComplete((response, error) -> {
            if (!terminal.compareAndSet(false, true)) return;
            try {
                if (error != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    log.error("Streaming chat failed for session {}", sessionId, cause);
                    send(emitter, "ERROR", Map.of(
                            "code", "CHAT_PROCESSING_FAILED",
                            "message", cause.getMessage() == null ? "Không thể tạo câu trả lời." : cause.getMessage(),
                            "elapsedMs", elapsedMs(startedAt),
                            "retryable", true
                    ));
                    emitter.complete();
                    return;
                }
                send(emitter, "DELTA", Map.of("text", response.answer == null ? "" : response.answer));
                send(emitter, "CITATIONS", Map.of("citations",
                        response.citations == null ? List.of() : response.citations));
                send(emitter, "COMPLETED", response);
                emitter.complete();
            } catch (IOException exception) {
                log.debug("SSE client disconnected for session {}", sessionId);
                emitter.complete();
            }
        });

        CompletableFuture.runAsync(() -> {
            if (!terminal.compareAndSet(false, true)) return;
            task.cancel(true);
            try {
                send(emitter, "ERROR", Map.of(
                        "code", "CHAT_DEADLINE_EXCEEDED",
                        "message", "Quá trình trả lời đã vượt quá 120 giây. Vui lòng thử lại.",
                        "elapsedMs", elapsedMs(startedAt),
                        "retryable", true
                ));
                emitter.complete();
            } catch (IOException exception) {
                emitter.complete();
            }
        }, CompletableFuture.delayedExecutor(120, TimeUnit.SECONDS));

        emitter.onTimeout(() -> {
            if (terminal.compareAndSet(false, true)) {
                task.cancel(true);
                emitter.complete();
            }
        });
        emitter.onError(error -> {
            terminal.set(true);
            task.cancel(true);
        });
        return emitter;
    }

    private static void sendPhase(
            SseEmitter emitter,
            AtomicBoolean terminal,
            String phase,
            long startedAt
    ) {
        if (terminal.get()) return;
        try {
            send(emitter, phase, Map.of(
                    "step", phase,
                    "status", "STARTED",
                    "messageKey", "chat.process." + phase.toLowerCase(java.util.Locale.ROOT),
                    "message", phaseMessage(phase),
                    "elapsedMs", elapsedMs(startedAt),
                    "metadata", Map.of()
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("SSE client disconnected.", exception);
        }
    }

    private static void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
    }

    private static String phaseMessage(String phase) {
        return switch (phase) {
            case "QUESTION_ANALYSIS" -> "Đang phân tích câu hỏi";
            case "SCOPE_CHECK" -> "Đang kiểm tra phạm vi tài liệu";
            case "QUERY_EXPANSION" -> "Đang làm rõ truy vấn";
            case "RETRIEVAL" -> "Đang tìm nội dung liên quan";
            case "EVIDENCE_SELECTION" -> "Đang chọn nguồn đa dạng";
            case "COVERAGE_CHECK" -> "Đang kiểm tra độ bao phủ bằng chứng";
            case "GENERATION_START" -> "Đang tạo câu trả lời";
            case "GROUNDING_CHECK" -> "Đang kiểm tra câu trả lời với tài liệu";
            case "REPAIR" -> "Đang hoàn thiện câu trả lời";
            case "CITATION_SAVE" -> "Đang lưu trích dẫn";
            default -> "Đang xử lý";
        };
    }

    private static int elapsedMs(long startedAt) {
        long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, elapsed));
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
