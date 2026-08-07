package com.courseqa.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.FeedbackRequest;
import com.courseqa.model.entity.ChatMessageFeedback;
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
    public ResponseEntity<ApiResponse<ChatMessageFeedback>> submitFeedback(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody FeedbackRequest request) {

        log.info("POST /api/chat/messages/{}/feedback - helpful: {}", messageId, request.getHelpful());

        ChatMessageFeedback feedback =
                feedbackService.submit(messageId, principal.userId(), request);

        return ResponseEntity.ok(ApiResponse.ok(feedback));
    }
}