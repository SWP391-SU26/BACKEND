package com.courseqa.repository;

import com.courseqa.model.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID>, JpaSpecificationExecutor<PaymentOrder> {
    Optional<PaymentOrder> findByVnpTxnRef(String vnpTxnRef);
    Optional<PaymentOrder> findByPaymentOrderIdAndUserId(UUID paymentOrderId, UUID userId);
    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    boolean existsByPlanId(UUID planId);
    Optional<PaymentOrder> findFirstByUserIdAndPlanCodeSnapshotAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, String planCodeSnapshot, String status, LocalDateTime expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.vnpTxnRef = :txnRef")
    Optional<PaymentOrder> findForUpdateByVnpTxnRef(@Param("txnRef") String txnRef);
}
