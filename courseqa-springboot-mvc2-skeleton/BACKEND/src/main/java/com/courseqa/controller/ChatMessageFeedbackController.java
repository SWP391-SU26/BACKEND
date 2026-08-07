package com.courseqa.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.FeedbackDto;
import com.courseqa.model.dto.FeedbackRequest;
import com.courseqa.security.JwtPrincipal;
import com.courseqa.service.ChatMessageFeedbackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
public class ChatMessageFeedbackController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageFeedbackController.class);

    private final ChatMessageFeedbackService feedbackService;

    public ChatMessageFeedbackController(ChatMessageFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/messages/{messageId}/feedback")
    public ResponseEntity<ApiResponse<FeedbackDto.FeedbackResponse>> submitFeedback(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody FeedbackRequest request) {

        log.info("POST /api/chat/messages/{}/feedback - helpful: {}", messageId, request.getHelpful());

        return ResponseEntity.ok(ApiResponse.ok(FeedbackDto.FeedbackResponse.from(
                feedbackService.submit(messageId, principal.userId(), request))));
    }

    /** The caller's own rating of one answer; data is null when they have not rated it. */
    @GetMapping("/messages/{messageId}/feedback")
    public ResponseEntity<ApiResponse<FeedbackDto.FeedbackResponse>> getFeedback(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                feedbackService.findOwn(messageId, principal.userId())
                        .map(FeedbackDto.FeedbackResponse::from)
                        .orElse(null)));
    }

    /**
     * Every rating the caller left in a session, so the chat can restore the
     * thumbs after a reload instead of showing every answer as unrated.
     */
    @GetMapping("/sessions/{sessionId}/feedback")
    public ResponseEntity<ApiResponse<List<FeedbackDto.FeedbackResponse>>> getSessionFeedback(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        List<FeedbackDto.FeedbackResponse> items =
                feedbackService.listOwnForSession(sessionId, principal.userId()).stream()
                        .map(FeedbackDto.FeedbackResponse::from)
                        .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
