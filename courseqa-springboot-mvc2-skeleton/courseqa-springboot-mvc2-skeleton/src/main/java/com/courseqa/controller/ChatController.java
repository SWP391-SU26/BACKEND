package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatSession;
import com.courseqa.model.entity.SavedNote;
import com.courseqa.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ChatController - API endpoints cho chat functionality
 * - Tạo/lấy session
 * - Hỏi câu hỏi (TODO: gọi Python)
 * - Lấy lịch sử chat
 * - Lưu/lấy notes
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class ChatController {

    private final ChatService chatService;

    /**
     * POST /api/chat/sessions
     * Tạo hoặc lấy session hiện tại
     *
     * @param request CreateSessionRequest: {userId, workspaceId}
     * @return ResponseEntity<ApiResponse<ChatSession>>
     */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSession>> createOrGetSession(@Valid @RequestBody CreateSessionRequest request) {
        logger.info("POST /api/chat/sessions - userId: {}, workspaceId: {}", request.getUserId(), request.getWorkspaceId());

        ChatSession session = chatService.createOrGetSession(request.getUserId(), request.getWorkspaceId());

        return ResponseEntity.ok(ApiResponse.success(session));
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
    public ResponseEntity<ApiResponse<ChatMessage>> askQuestion(
            @PathVariable Long sessionId,
            @Valid @RequestBody AskQuestionRequest request) {
        logger.info("POST /api/chat/sessions/{}/ask - question: {}", sessionId, request.getQuestion());

        // TODO: Implement askQuestion logic:
        // 1. Gọi chatService.askQuestion(sessionId, question)
        // 2. TODO sẽ implement khi TV6 confirm API contract

        throw new UnsupportedOperationException("askQuestion not implemented yet - waiting for TV6 API contract");
    }

    /**
     * GET /api/chat/sessions/{sessionId}/history
     * Lấy lịch sử chat (50 messages gần nhất)
     *
     * @param sessionId ID của session
     * @return ResponseEntity<ApiResponse<List<ChatMessage>>>
     */
    @GetMapping("/sessions/{sessionId}/history")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getHistory(@PathVariable Long sessionId) {
        logger.info("GET /api/chat/sessions/{}/history", sessionId);

        List<ChatMessage> history = chatService.getHistory(sessionId);

        return ResponseEntity.ok(ApiResponse.success(history));
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
        logger.info("POST /api/chat/notes - userId: {}, workspaceId: {}", request.getUserId(), request.getWorkspaceId());

        SavedNote note = chatService.saveNote(request.getUserId(), request.getWorkspaceId(), request.getContent());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(note));
    }

    /**
     * GET /api/chat/notes/workspace/{workspaceId}
     * Lấy danh sách notes của workspace
     *
     * @param workspaceId ID của workspace
     * @return ResponseEntity<ApiResponse<List<SavedNote>>>
     */
    @GetMapping("/notes/workspace/{workspaceId}")
    public ResponseEntity<ApiResponse<List<SavedNote>>> getNotes(@PathVariable Long workspaceId) {
        logger.info("GET /api/chat/notes/workspace/{}", workspaceId);

        List<SavedNote> notes = chatService.getNotes(workspaceId);

        return ResponseEntity.ok(ApiResponse.success(notes));
    }

    // ==================== Request DTOs ====================

    /**
     * Request DTO cho createOrGetSession
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateSessionRequest {
        @NotNull(message = "userId is required")
        private Long userId;

        @NotNull(message = "workspaceId is required")
        private Long workspaceId;
    }

    /**
     * Request DTO cho askQuestion
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AskQuestionRequest {
        @NotBlank(message = "question is required and cannot be empty")
        private String question;
    }

    /**
     * Request DTO cho saveNote
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SaveNoteRequest {
        @NotNull(message = "userId is required")
        private Long userId;

        @NotNull(message = "workspaceId is required")
        private Long workspaceId;

        @NotBlank(message = "content is required and cannot be empty")
        private String content;
    }
}
