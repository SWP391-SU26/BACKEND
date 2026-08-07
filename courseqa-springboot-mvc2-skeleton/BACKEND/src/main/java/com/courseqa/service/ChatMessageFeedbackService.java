package com.courseqa.service;

import com.courseqa.model.dto.FeedbackRequest;
import com.courseqa.model.entity.ChatMessage;
import com.courseqa.model.entity.ChatMessageFeedback;
import com.courseqa.repository.ChatMessageFeedbackRepository;
import com.courseqa.repository.ChatMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.courseqa.service.ChatService;


import java.util.Optional;
import java.util.UUID;

@Service
public class ChatMessageFeedbackService {

    private static final String ASSISTANT_ROLE = "assistant";

    private final ChatMessageFeedbackRepository feedbackRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    public ChatMessageFeedbackService(ChatMessageFeedbackRepository feedbackRepository,
                                      ChatMessageRepository chatMessageRepository,
                                      ChatService chatService) {
        this.feedbackRepository = feedbackRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatService = chatService;
    }

    @Transactional
    public ChatMessageFeedback submit(UUID messageId, UUID userId, FeedbackRequest request) {

        validate(request);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Message not found."));

        if (!ASSISTANT_ROLE.equalsIgnoreCase(message.getSenderRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Feedback can only be given on assistant messages.");
        }
          
        chatService.requireSessionOwner(message.getChatSessionId(), userId);

        Optional<ChatMessageFeedback> existing =
                feedbackRepository.findByMessageIdAndUserId(messageId, userId);

        if (existing.isPresent()) {
            ChatMessageFeedback feedback = existing.get();
            feedback.setHelpful(request.getHelpful());
            feedback.setReasonCode(request.getReasonCode());
            feedback.setComment(request.getComment());
            return feedbackRepository.save(feedback);
        }

        return feedbackRepository.save(new ChatMessageFeedback(
                messageId, userId, request.getHelpful(),
                request.getReasonCode(), request.getComment()));
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
        if (request.getComment() != null && request.getComment().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "comment must be 1000 characters or fewer.");
        }
    }
}