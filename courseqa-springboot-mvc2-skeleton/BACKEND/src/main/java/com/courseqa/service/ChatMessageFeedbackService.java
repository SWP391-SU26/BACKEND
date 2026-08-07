package com.courseqa.service;

import com.courseqa.model.dto.FeedbackDto;
import com.courseqa.model.dto.FeedbackRequest;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatMessageFeedback;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.FeedbackReason;
import com.courseqa.repository.ChatMessageFeedbackRepository;
import com.courseqa.repository.ChatMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatMessageFeedbackService {

    private static final String ASSISTANT_ROLE = "assistant";
    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int DEFAULT_STATS_WINDOW_DAYS = 30;
    private static final int MAX_NEGATIVE_PAGE_SIZE = 100;

    private final ChatMessageFeedbackRepository feedbackRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final EvaluationService evaluationService;

    public ChatMessageFeedbackService(ChatMessageFeedbackRepository feedbackRepository,
                                      ChatMessageRepository chatMessageRepository,
                                      ChatService chatService,
                                      EvaluationService evaluationService) {
        this.feedbackRepository = feedbackRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
        this.evaluationService = evaluationService;
    }

    // ---------------------------------------------------------------- write

    @Transactional
    public ChatMessageFeedback submit(UUID messageId, UUID userId, FeedbackRequest request) {

        validate(request);

        ChatMessage message = requireAssistantMessage(messageId);
        chatService.requireSessionOwner(message.getChatSessionId(), userId);

        Optional<ChatMessageFeedback> existing =
                feedbackRepository.findByMessageIdAndUserId(messageId, userId);

        if (existing.isPresent()) {
            return applyAndSave(existing.get(), request);
        }

        try {
            return feedbackRepository.save(new ChatMessageFeedback(
                    messageId, userId, request.getHelpful(),
                    request.getReasonCode(), trimmedComment(request)));
        } catch (DataIntegrityViolationException raced) {
            // Two rapid clicks land here: the unique (message_id, user_id) index
            // rejected the second insert. Treat it as the edit it really was.
            ChatMessageFeedback winner = feedbackRepository
                    .findByMessageIdAndUserId(messageId, userId)
                    .orElseThrow(() -> raced);
            return applyAndSave(winner, request);
        }
    }

    private ChatMessageFeedback applyAndSave(ChatMessageFeedback feedback, FeedbackRequest request) {
        feedback.setHelpful(request.getHelpful());
        feedback.setReasonCode(request.getReasonCode());
        feedback.setComment(trimmedComment(request));
        return feedbackRepository.save(feedback);
    }

    // ----------------------------------------------------------------- read

    /** The caller's own feedback on one answer, or empty if they have not rated it. */
    @Transactional(readOnly = true)
    public Optional<ChatMessageFeedback> findOwn(UUID messageId, UUID userId) {
        ChatMessage message = requireAssistantMessage(messageId);
        chatService.requireSessionOwner(message.getChatSessionId(), userId);
        return feedbackRepository.findByMessageIdAndUserId(messageId, userId);
    }

    /**
     * Every rating the caller has left in one session. The chat needs this on load,
     * otherwise a refresh loses which answers were already rated.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageFeedback> listOwnForSession(UUID sessionId, UUID userId) {
        chatService.requireSessionOwner(sessionId, userId);
        List<UUID> messageIds = chatMessageRepository
                .findByChatSessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(ChatMessage::getMessageId)
                .toList();
        if (messageIds.isEmpty()) {
            return List.of();
        }
        return feedbackRepository.findByUserIdAndMessageIdIn(userId, messageIds);
    }

    // ------------------------------------------------------------- insights

    @Transactional(readOnly = true)
    public FeedbackDto.FeedbackStatsResponse stats(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from != null ? from : nowUtc().minusDays(DEFAULT_STATS_WINDOW_DAYS);
        LocalDateTime end = to != null ? to : nowUtc();
        requireOrderedRange(start, end);

        FeedbackDto.FeedbackStatsResponse response = new FeedbackDto.FeedbackStatsResponse();
        response.from = start;
        response.to = end;
        response.total = feedbackRepository.countByCreatedAtBetween(start, end);
        response.helpfulCount = feedbackRepository.countByHelpfulAndCreatedAtBetween(true, start, end);
        response.notHelpfulCount = feedbackRepository.countByHelpfulAndCreatedAtBetween(false, start, end);
        response.promotedCount =
                feedbackRepository.countByPromotedQuestionIdIsNotNullAndCreatedAtBetween(start, end);
        response.helpfulRate = response.total == 0
                ? null
                : (double) response.helpfulCount / response.total;

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (FeedbackReason reason : FeedbackReason.values()) {
            byReason.put(reason.name(), 0L);
        }
        for (Object[] row : feedbackRepository.countNotHelpfulByReason(start, end)) {
            FeedbackReason reason = (FeedbackReason) row[0];
            byReason.put(reason.name(), ((Number) row[1]).longValue());
        }
        response.byReason = byReason;
        return response;
    }

    /**
     * The answers users rejected, newest first, each paired with the question that
     * produced it. This is the review queue for turning complaints into benchmarks.
     */
    @Transactional(readOnly = true)
    public FeedbackDto.NegativeFeedbackPage recentNegative(LocalDateTime from, LocalDateTime to, int limit) {
        LocalDateTime start = from != null ? from : nowUtc().minusDays(DEFAULT_STATS_WINDOW_DAYS);
        LocalDateTime end = to != null ? to : nowUtc();
        requireOrderedRange(start, end);
        int size = Math.min(Math.max(limit, 1), MAX_NEGATIVE_PAGE_SIZE);

        List<ChatMessageFeedback> negatives = feedbackRepository
                .findByHelpfulFalseAndCreatedAtBetweenOrderByCreatedAtDesc(
                        start, end, PageRequest.of(0, size));

        FeedbackDto.NegativeFeedbackPage page = new FeedbackDto.NegativeFeedbackPage();
        page.totalNegative = feedbackRepository.countByHelpfulAndCreatedAtBetween(false, start, end);
        page.items = negatives.stream().map(this::toNegativeItem).toList();
        return page;
    }

    private FeedbackDto.NegativeFeedbackItem toNegativeItem(ChatMessageFeedback feedback) {
        FeedbackDto.NegativeFeedbackItem item = new FeedbackDto.NegativeFeedbackItem();
        item.feedbackId = feedback.getFeedbackId();
        item.messageId = feedback.getMessageId();
        item.reasonCode = feedback.getReasonCode();
        item.comment = feedback.getComment();
        item.createdAt = feedback.getCreatedAt();
        item.promotedQuestionId = feedback.getPromotedQuestionId();
        item.promotedAt = feedback.getPromotedAt();

        chatMessageRepository.findById(feedback.getMessageId()).ifPresent(answer -> {
            item.chatSessionId = answer.getChatSessionId();
            item.answerText = answer.getMessageContent();
            item.llmModel = answer.getLlmModel();
            item.questionIntent = answer.getQuestionIntent();
            item.questionText = precedingQuestion(answer).orElse(null);
        });
        return item;
    }

    // ------------------------------------------------------------- promotion

    /**
     * Copies a rejected answer into an evaluation dataset as a new question, closing
     * the loop from "users said this was wrong" back to "we measure it every run".
     */
    @Transactional
    public FeedbackDto.PromoteResponse promote(UUID feedbackId, FeedbackDto.PromoteRequest request) {
        if (request == null || request.datasetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datasetId is required.");
        }
        String groundTruth = request.groundTruthAnswer == null ? "" : request.groundTruthAnswer.trim();
        if (groundTruth.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "groundTruthAnswer is required - a benchmark question needs an expected answer.");
        }

        ChatMessageFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Feedback not found."));

        if (feedback.isHelpful()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only answers marked not helpful can be promoted to a dataset.");
        }
        if (feedback.getPromotedQuestionId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This feedback has already been promoted to an evaluation question.");
        }

        String questionText = request.questionText == null ? "" : request.questionText.trim();
        if (questionText.isEmpty()) {
            ChatMessage answer = chatMessageRepository.findById(feedback.getMessageId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "The rated message no longer exists."));
            questionText = precedingQuestion(answer).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Could not recover the original question; supply questionText explicitly."));
        }

        EvaluationQuestion created = evaluationService.addQuestion(
                request.datasetId, questionText, groundTruth);

        feedback.markPromoted(created.getEvaluationQuestionId());
        feedbackRepository.save(feedback);

        FeedbackDto.PromoteResponse response = new FeedbackDto.PromoteResponse();
        response.feedbackId = feedback.getFeedbackId();
        response.datasetId = request.datasetId;
        response.evaluationQuestionId = created.getEvaluationQuestionId();
        response.questionNo = created.getQuestionNo();
        response.questionText = created.getQuestionText();
        return response;
    }

    // --------------------------------------------------------------- helpers

    /** The user turn immediately before an assistant answer in the same session. */
    private Optional<String> precedingQuestion(ChatMessage answer) {
        if (answer.getChatSessionId() == null || answer.getCreatedAt() == null) {
            return Optional.empty();
        }
        return chatMessageRepository
                .findByChatSessionIdOrderByCreatedAtAsc(answer.getChatSessionId()).stream()
                .filter(candidate -> !ASSISTANT_ROLE.equalsIgnoreCase(candidate.getSenderRole()))
                .filter(candidate -> candidate.getCreatedAt() != null
                        && candidate.getCreatedAt().isBefore(answer.getCreatedAt()))
                .max(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(ChatMessage::getMessageContent)
                .filter(text -> text != null && !text.isBlank());
    }

    private ChatMessage requireAssistantMessage(UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Message not found."));
        if (!ASSISTANT_ROLE.equalsIgnoreCase(message.getSenderRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Feedback can only be given on assistant messages.");
        }
        return message;
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static void requireOrderedRange(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The 'from' date must not be after the 'to' date.");
        }
    }

    private String trimmedComment(FeedbackRequest request) {
        if (request.getComment() == null) {
            return null;
        }
        String trimmed = request.getComment().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validate(FeedbackRequest request) {
        if (request.getHelpful() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "helpful is required.");
        }
        if (!request.getHelpful() && request.getReasonCode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "reasonCode is required when helpful is false.");
        }
        if (request.getHelpful() && request.getReasonCode() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "reasonCode is not allowed when helpful is true.");
        }
        if (request.getComment() != null && request.getComment().length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "comment must be " + MAX_COMMENT_LENGTH + " characters or fewer.");
        }
    }
}
