package com.courseqa.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.dto.FeedbackDto;
import com.courseqa.service.ChatMessageFeedbackService;

/**
 * Read side of FR-09 for researchers and admins: what users think of the answers,
 * and the one action that matters - turning a rejected answer into a benchmark case.
 *
 * Sits under /api/evaluation so SecurityConfig's existing ADMIN+RESEARCHER rule
 * applies, and because this is evaluation tooling rather than user-facing chat.
 */
@RestController
@RequestMapping("/api/evaluation/feedback")
@CrossOrigin
public class FeedbackInsightsController {

    private static final int DEFAULT_NEGATIVE_LIMIT = 20;

    private final ChatMessageFeedbackService feedbackService;

    public FeedbackInsightsController(ChatMessageFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** Helpful rate and reason breakdown. Defaults to the last 30 days. */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<FeedbackDto.FeedbackStatsResponse>> stats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(ApiResponse.ok(feedbackService.stats(from, to)));
    }

    /** The review queue: answers users rejected, with the question that caused them. */
    @GetMapping("/negative")
    public ResponseEntity<ApiResponse<FeedbackDto.NegativeFeedbackPage>> negative(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "" + DEFAULT_NEGATIVE_LIMIT) int limit) {

        return ResponseEntity.ok(ApiResponse.ok(feedbackService.recentNegative(from, to, limit)));
    }

    /** Adds the rejected answer's question to an evaluation dataset as a new case. */
    @PostMapping("/{feedbackId}/promote")
    public ResponseEntity<ApiResponse<FeedbackDto.PromoteResponse>> promote(
            @PathVariable UUID feedbackId,
            @RequestBody FeedbackDto.PromoteRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(feedbackService.promote(feedbackId, request)));
    }
}
