package com.courseqa.repository;

import com.courseqa.model.entity.UploadSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    List<UploadSession> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<UploadSession> findByStatusAndUpdatedAtBefore(String status, LocalDateTime cutoff);
}
