package com.courseqa.repository;

import com.courseqa.model.entity.ChatMessageFeedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageFeedbackRepository
        extends JpaRepository<ChatMessageFeedback, UUID> {

    Optional<ChatMessageFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);

    /** One round trip for a whole session, so the chat can render every thumb at once. */
    List<ChatMessageFeedback> findByUserIdAndMessageIdIn(UUID userId, Collection<UUID> messageIds);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByHelpfulAndCreatedAtBetween(boolean helpful, LocalDateTime from, LocalDateTime to);

    long countByPromotedQuestionIdIsNotNullAndCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT f.reasonCode, COUNT(f)
            FROM ChatMessageFeedback f
            WHERE f.helpful = false
              AND f.reasonCode IS NOT NULL
              AND f.createdAt BETWEEN :from AND :to
            GROUP BY f.reasonCode
            """)
    List<Object[]> countNotHelpfulByReason(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    List<ChatMessageFeedback> findByHelpfulFalseAndCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);
}
