package com.courseqa.repository;

import com.courseqa.model.entity.ChatMessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageFeedbackRepository
        extends JpaRepository<ChatMessageFeedback, UUID> {

    Optional<ChatMessageFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);
}