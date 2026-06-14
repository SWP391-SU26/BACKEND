package com.courseqa.repository;

import com.courseqa.model.entity.AnswerCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnswerCitationRepository extends JpaRepository<AnswerCitation, UUID> {
    List<AnswerCitation> findAllByOrderByCreatedAtDesc();

    List<AnswerCitation> findByAssistantMessageId(UUID assistantMessageId);

    List<AnswerCitation> findByAssistantMessageIdOrderByCitationOrderAsc(UUID assistantMessageId);
}
