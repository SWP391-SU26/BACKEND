package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.repository.PaymentOrderRepository;
import com.courseqa.repository.SubscriptionHistoryRepository;
import com.courseqa.repository.SubscriptionPlanRepository;
import com.courseqa.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlanServiceTest {
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final PaymentOrderRepository orders = mock(PaymentOrderRepository.class);
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final SubscriptionHistoryRepository history = mock(SubscriptionHistoryRepository.class);
    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(plans, new ObjectMapper(), orders, subscriptions, history);
        when(plans.save(any(SubscriptionPlan.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsPaidPlanWithNormalizedCodeAndBenefits() {
        PaymentDto.PlanUpsertRequest request = paidRequest();
        request.planCode = " pro_plus ";

        PaymentDto.PlanResponse response = service.create(request);

        assertEquals("PRO_PLUS", response.planCode);
        assertEquals(99_000L, response.priceVnd);
        assertEquals(List.of("100 documents", "Priority storage"), response.benefits);
        assertTrue(response.isActive);
    }

    @Test
    void rejectsChangingPlanCodeAfterCreation() {
        SubscriptionPlan plan = plan("PRO");
        when(plans.findById(plan.getPlanId())).thenReturn(Optional.of(plan));
        PaymentDto.PlanUpsertRequest request = paidRequest();
        request.planCode = "PLUS";

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.update(plan.getPlanId(), request));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void referencedPlanIsDeactivatedInsteadOfDeleted() {
        SubscriptionPlan plan = plan("PRO");
        when(plans.findById(plan.getPlanId())).thenReturn(Optional.of(plan));
        when(orders.existsByPlanId(plan.getPlanId())).thenReturn(true);

        PaymentDto.PlanDeleteResponse result = service.delete(plan.getPlanId());

        assertFalse(result.deleted);
        assertTrue(result.deactivated);
        assertFalse(plan.getIsActive());
        verify(plans).save(plan);
    }

    @Test
    void freeFallbackCannotBeDeleted() {
        SubscriptionPlan plan = plan("FREE");
        when(plans.findById(plan.getPlanId())).thenReturn(Optional.of(plan));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.delete(plan.getPlanId()));

        assertEquals(409, error.getStatusCode().value());
    }

    private static PaymentDto.PlanUpsertRequest paidRequest() {
        PaymentDto.PlanUpsertRequest request = new PaymentDto.PlanUpsertRequest();
        request.planCode = "PRO";
        request.displayName = "PRO Plus";
        request.priceVnd = 99_000L;
        request.durationDays = 30;
        request.maxFileBytes = 10L * 1024 * 1024;
        request.maxDocuments = 100;
        request.maxStorageBytes = 1024L * 1024 * 1024;
        request.maxPersonalWorkspaces = 20;
        request.benefits = List.of("100 documents", "Priority storage");
        request.isActive = true;
        return request;
    }

    private static SubscriptionPlan plan(String code) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanId(UUID.randomUUID());
        plan.setPlanCode(code);
        plan.setDisplayName(code);
        plan.setPriceVnd("FREE".equals(code) ? 0L : 49_000L);
        plan.setDurationDays("FREE".equals(code) ? null : 30);
        plan.setMaxFileBytes(10L * 1024 * 1024);
        plan.setMaxDocuments(10);
        plan.setMaxStorageBytes(100L * 1024 * 1024);
        plan.setMaxPersonalWorkspaces(1);
        plan.setBenefitsJson("[]");
        plan.setIsActive(true);
        return plan;
    }
}
