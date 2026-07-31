package com.courseqa.repository;

import com.courseqa.model.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Page<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId, Pageable pageable);

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId);

    List<ChatMessage> findTop10ByChatSessionIdOrderByCreatedAtDesc(UUID chatSessionId);

    List<ChatMessage> findTop12ByChatSessionIdOrderByCreatedAtDesc(UUID chatSessionId);
}
