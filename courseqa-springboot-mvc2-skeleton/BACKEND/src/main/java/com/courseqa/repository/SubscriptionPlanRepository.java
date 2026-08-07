package com.courseqa.repository;

import com.courseqa.model.entity.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    Optional<SubscriptionPlan> findByPlanCode(String planCode);
    boolean existsByPlanCodeIgnoreCase(String planCode);
    Optional<SubscriptionPlan> findByPlanCodeAndIsActiveTrue(String planCode);
    List<SubscriptionPlan> findByIsActiveTrueOrderByPriceVndAsc();
    List<SubscriptionPlan> findAllByOrderByPriceVndAsc();
}
