package com.courseqa.repository;

import com.courseqa.model.entity.ChatSessionDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionDocumentRepository extends JpaRepository<ChatSessionDocument, UUID> {
    List<ChatSessionDocument> findByChatSessionId(UUID chatSessionId);
    void deleteByChatSessionId(UUID chatSessionId);
}
