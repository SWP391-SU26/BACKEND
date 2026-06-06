package com.courseqa.repository;

import com.courseqa.model.entity.AnswerCitation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerCitationRepository extends JpaRepository<AnswerCitation, UUID> {
    List<AnswerCitation> findAllByOrderByCreatedAtDesc();

    List<AnswerCitation> findByAssistantMessageIdOrderByCitationOrderAsc(UUID assistantMessageId);
}
