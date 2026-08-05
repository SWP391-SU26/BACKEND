package com.courseqa.repository;

import com.courseqa.model.entity.PaymentCallbackAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCallbackAuditRepository extends JpaRepository<PaymentCallbackAudit, UUID> {
    List<PaymentCallbackAudit> findByPaymentOrderIdOrderByReceivedAtDesc(UUID paymentOrderId);
}
