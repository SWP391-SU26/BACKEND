package com.courseqa.repository;

import com.courseqa.model.entity.ProcessingJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    List<ProcessingJob> findByStatusOrderByCreatedAtDesc(String status);

    List<ProcessingJob> findAllByOrderByCreatedAtDesc();

    List<ProcessingJob> findByStatusInAndUpdatedAtBefore(List<String> statuses, LocalDateTime cutoff);
}
