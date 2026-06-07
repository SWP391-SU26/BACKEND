package com.courseqa.repository;

import com.courseqa.model.entity.RetrievalQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrievalQueryRepository extends JpaRepository<RetrievalQuery, UUID> {
    List<RetrievalQuery> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<RetrievalQuery> findAllByOrderByCreatedAtDesc();
}
