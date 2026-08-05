package com.courseqa.repository;

import com.courseqa.model.entity.SubscriptionHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, UUID> {
    List<SubscriptionHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByPaymentOrderId(UUID paymentOrderId);
    boolean existsByPlanId(UUID planId);
}
