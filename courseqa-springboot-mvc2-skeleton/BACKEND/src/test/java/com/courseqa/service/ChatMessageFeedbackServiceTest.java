package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.FeedbackDto;
import com.courseqa.model.dto.FeedbackRequest;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatMessageFeedback;
import com.courseqa.model.entity.EvaluationQuestion;
import com.courseqa.model.entity.FeedbackReason;
import com.courseqa.repository.ChatMessageFeedbackRepository;
import com.courseqa.repository.ChatMessageRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ChatMessageFeedbackServiceTest {

    private final ChatMessageFeedbackRepository feedbackRepository =
            mock(ChatMessageFeedbackRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ChatService chatService = mock(ChatService.class);
    private final EvaluationService evaluationService = mock(EvaluationService.class);

    private ChatMessageFeedbackService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChatMessageFeedbackService(
                feedbackRepository, chatMessageRepository, chatService, evaluationService);
        when(feedbackRepository.save(any(ChatMessageFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------ submitting

    @Test
    void rejectsFeedbackWithoutHelpfulFlag() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submit(answerId, userId, request(null, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(chatMessageRepository, never()).findById(any());
    }

    @Test
    void requiresReasonWhenAnswerIsNotHelpful() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submit(answerId, userId, request(false, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsReasonOnPositiveFeedback() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submit(answerId, userId,
                        request(true, FeedbackReason.WRONG_INFORMATION, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void refusesFeedbackOnAUserMessage() {
        ChatMessage userTurn = message(answerId, "user", "Triết học là gì?", LocalDateTime.now());
        when(chatMessageRepository.findById(answerId)).thenReturn(Optional.of(userTurn));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submit(answerId, userId, request(true, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void storesFeedbackOnAnAssistantAnswer() {
        givenAssistantAnswer();
        when(feedbackRepository.findByMessageIdAndUserId(answerId, userId))
                .thenReturn(Optional.empty());

        ChatMessageFeedback saved = service.submit(answerId, userId,
                request(true, null, "  rõ ràng  "));

        assertTrue(saved.isHelpful());
        assertEquals("rõ ràng", saved.getComment(), "comment should be trimmed");
        verify(chatService).requireSessionOwner(sessionId, userId);
    }

    @Test
    void changingYourMindUpdatesTheExistingRow() {
        givenAssistantAnswer();
        ChatMessageFeedback existing = new ChatMessageFeedback(
                answerId, userId, true, null, null);
        when(feedbackRepository.findByMessageIdAndUserId(answerId, userId))
                .thenReturn(Optional.of(existing));

        ChatMessageFeedback saved = service.submit(answerId, userId,
                request(false, FeedbackReason.MISSING_CITATION, null));

        assertSame(existing, saved, "should reuse the row, not insert a second one");
        assertEquals(FeedbackReason.MISSING_CITATION, saved.getReasonCode());
        assertTrue(!saved.isHelpful());
    }

    @Test
    void concurrentDoubleClickResolvesToAnUpdateInsteadOf500() {
        givenAssistantAnswer();
        ChatMessageFeedback winner = new ChatMessageFeedback(answerId, userId, true, null, null);
        when(feedbackRepository.findByMessageIdAndUserId(answerId, userId))
                .thenReturn(Optional.empty())      // first read: nothing there yet
                .thenReturn(Optional.of(winner));  // after the unique index rejects us
        when(feedbackRepository.save(any(ChatMessageFeedback.class)))
                .thenThrow(new DataIntegrityViolationException("UQ_cmf_message_user"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageFeedback saved = service.submit(answerId, userId,
                request(false, FeedbackReason.TOO_SLOW, null));

        assertSame(winner, saved);
        assertEquals(FeedbackReason.TOO_SLOW, saved.getReasonCode());
    }

    // --------------------------------------------------------------- reading

    @Test
    void sessionFeedbackIsEmptyWhenTheSessionHasNoMessages() {
        when(chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of());

        assertTrue(service.listOwnForSession(sessionId, userId).isEmpty());
        verify(chatService).requireSessionOwner(sessionId, userId);
        verify(feedbackRepository, never()).findByUserIdAndMessageIdIn(any(), anyList());
    }

    @Test
    void sessionFeedbackLooksUpEveryMessageInOneCall() {
        ChatMessage question = message(UUID.randomUUID(), "user", "Q", LocalDateTime.now());
        ChatMessage answer = message(answerId, "assistant", "A", LocalDateTime.now());
        when(chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(question, answer));
        ChatMessageFeedback stored = new ChatMessageFeedback(answerId, userId, true, null, null);
        when(feedbackRepository.findByUserIdAndMessageIdIn(eq(userId), anyList()))
                .thenReturn(List.of(stored));

        List<ChatMessageFeedback> result = service.listOwnForSession(sessionId, userId);

        assertEquals(1, result.size());
        assertSame(stored, result.get(0));
    }

    // --------------------------------------------------------------- insights

    @Test
    void statsReportHelpfulRateAndZeroFillEveryReason() {
        when(feedbackRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);
        when(feedbackRepository.countByHelpfulAndCreatedAtBetween(eq(true), any(), any()))
                .thenReturn(7L);
        when(feedbackRepository.countByHelpfulAndCreatedAtBetween(eq(false), any(), any()))
                .thenReturn(3L);
        when(feedbackRepository.countByPromotedQuestionIdIsNotNullAndCreatedAtBetween(any(), any()))
                .thenReturn(1L);
        when(feedbackRepository.countNotHelpfulByReason(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] { FeedbackReason.WRONG_INFORMATION, 2L }));

        FeedbackDto.FeedbackStatsResponse stats = service.stats(null, null);

        assertEquals(0.7d, stats.helpfulRate, 0.0001);
        assertEquals(1L, stats.promotedCount);
        assertEquals(2L, stats.byReason.get("WRONG_INFORMATION"));
        assertEquals(0L, stats.byReason.get("OFF_TOPIC"), "unused reasons should read as 0, not be absent");
        assertEquals(FeedbackReason.values().length, stats.byReason.size());
    }

    @Test
    void helpfulRateIsNullRatherThanZeroWhenNobodyHasVoted() {
        when(feedbackRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(feedbackRepository.countNotHelpfulByReason(any(), any())).thenReturn(List.of());

        FeedbackDto.FeedbackStatsResponse stats = service.stats(null, null);

        assertNull(stats.helpfulRate, "0/0 must not be reported as a 0% helpful rate");
    }

    @Test
    void statsRejectABackwardsDateRange() {
        LocalDateTime now = LocalDateTime.now();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.stats(now, now.minusDays(1)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void negativeQueuePairsTheAnswerWithTheQuestionThatCausedIt() {
        LocalDateTime askedAt = LocalDateTime.now().minusMinutes(2);
        ChatMessage question = message(UUID.randomUUID(), "user", "Triết học là gì?", askedAt);
        ChatMessage answer = message(answerId, "assistant", "Sai bét", askedAt.plusSeconds(5));
        ChatMessageFeedback feedback = new ChatMessageFeedback(
                answerId, userId, false, FeedbackReason.WRONG_INFORMATION, "sai rồi");

        when(feedbackRepository.findByHelpfulFalseAndCreatedAtBetweenOrderByCreatedAtDesc(
                any(), any(), any(Pageable.class))).thenReturn(List.of(feedback));
        when(feedbackRepository.countByHelpfulAndCreatedAtBetween(eq(false), any(), any()))
                .thenReturn(1L);
        when(chatMessageRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(question, answer));

        FeedbackDto.NegativeFeedbackPage page = service.recentNegative(null, null, 20);

        assertEquals(1, page.items.size());
        assertEquals("Triết học là gì?", page.items.get(0).questionText);
        assertEquals("Sai bét", page.items.get(0).answerText);
        assertEquals(1L, page.totalNegative);
    }

    // -------------------------------------------------------------- promoting

    @Test
    void refusesToPromoteAnAnswerUsersLiked() {
        UUID feedbackId = UUID.randomUUID();
        when(feedbackRepository.findById(feedbackId))
                .thenReturn(Optional.of(new ChatMessageFeedback(answerId, userId, true, null, null)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.promote(feedbackId, promoteRequest("Đáp án đúng")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(evaluationService, never()).addQuestion(any(), any(), any());
    }

    @Test
    void requiresAGroundTruthAnswerToBuildABenchmarkCase() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.promote(UUID.randomUUID(), promoteRequest("   ")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void refusesToPromoteTheSameFeedbackTwice() throws Exception {
        UUID feedbackId = UUID.randomUUID();
        ChatMessageFeedback feedback = new ChatMessageFeedback(
                answerId, userId, false, FeedbackReason.WRONG_INFORMATION, null);
        feedback.markPromoted(UUID.randomUUID());
        when(feedbackRepository.findById(feedbackId)).thenReturn(Optional.of(feedback));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.promote(feedbackId, promoteRequest("Đáp án đúng")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(evaluationService, never()).addQuestion(any(), any(), any());
    }

    @Test
    void promotingCopiesTheOriginalQuestionIntoTheDatasetAndMarksTheFeedback() throws Exception {
        UUID feedbackId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID createdQuestionId = UUID.randomUUID();
        LocalDateTime askedAt = LocalDateTime.now().minusMinutes(1);

        ChatMessageFeedback feedback = new ChatMessageFeedback(
                answerId, userId, false, FeedbackReason.WRONG_INFORMATION, null);
        setField(feedback, "feedbackId", feedbackId);
        when(feedbackRepository.findById(feedbackId)).thenReturn(Optional.of(feedback));

        ChatMessage question = message(UUID.randomUUID(), "user", "Triết học là gì?", askedAt);
        ChatMessage answer = message(answerId, "assistant", "Sai bét", askedAt.plusSeconds(5));
        when(chatMessageRepository.findById(answerId)).thenReturn(Optional.of(answer));
        when(chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(question, answer));

        EvaluationQuestion created = new EvaluationQuestion();
        setField(created, "evaluationQuestionId", createdQuestionId);
        setField(created, "questionNo", 4);
        setField(created, "questionText", "Triết học là gì?");
        when(evaluationService.addQuestion(datasetId, "Triết học là gì?", "Đáp án đúng"))
                .thenReturn(created);

        FeedbackDto.PromoteRequest request = promoteRequest("Đáp án đúng");
        request.datasetId = datasetId;

        FeedbackDto.PromoteResponse response = service.promote(feedbackId, request);

        assertEquals(createdQuestionId, response.evaluationQuestionId);
        assertEquals(4, response.questionNo);
        assertEquals(createdQuestionId, feedback.getPromotedQuestionId(),
                "the feedback must record what it became, so it cannot be promoted twice");
        verify(feedbackRepository).save(feedback);
    }

    // --------------------------------------------------------------- fixtures

    private void givenAssistantAnswer() {
        ChatMessage answer = message(answerId, "assistant", "Câu trả lời", LocalDateTime.now());
        when(chatMessageRepository.findById(answerId)).thenReturn(Optional.of(answer));
    }

    private ChatMessage message(UUID id, String role, String content, LocalDateTime createdAt) {
        ChatMessage message = new ChatMessage();
        setFieldQuietly(message, "messageId", id);
        setFieldQuietly(message, "chatSessionId", sessionId);
        setFieldQuietly(message, "senderRole", role);
        setFieldQuietly(message, "messageContent", content);
        setFieldQuietly(message, "createdAt", createdAt);
        return message;
    }

    private FeedbackRequest request(Boolean helpful, FeedbackReason reason, String comment) {
        FeedbackRequest request = new FeedbackRequest();
        request.setHelpful(helpful);
        request.setReasonCode(reason);
        request.setComment(comment);
        return request;
    }

    private FeedbackDto.PromoteRequest promoteRequest(String groundTruth) {
        FeedbackDto.PromoteRequest request = new FeedbackDto.PromoteRequest();
        request.datasetId = UUID.randomUUID();
        request.groundTruthAnswer = groundTruth;
        return request;
    }

    /** These entities are JPA-populated and have no setters for identity fields. */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setFieldQuietly(Object target, String name, Object value) {
        try {
            setField(target, name, value);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot seed " + name, failure);
        }
    }
}
