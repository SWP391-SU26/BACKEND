package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.PaymentOrder;
import com.courseqa.model.entity.SubscriptionHistory;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.model.entity.UserSubscription;
import com.courseqa.repository.PaymentOrderRepository;
import com.courseqa.repository.SubscriptionHistoryRepository;
import com.courseqa.repository.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VnpaySettlementServiceTest {
    private final PaymentOrderRepository orders = mock(PaymentOrderRepository.class);
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final SubscriptionHistoryRepository history = mock(SubscriptionHistoryRepository.class);
    private final PlanService plans = mock(PlanService.class);
    private final UUID planId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private VnpaySettlementService settlement;
    private PaymentOrder order;

    @BeforeEach
    void setUp() {
        settlement = new VnpaySettlementService(orders, subscriptions, history, plans);
        SubscriptionPlan pro = new SubscriptionPlan();
        pro.setPlanId(planId);
        pro.setPlanCode("PRO");
        when(plans.requireById(planId)).thenReturn(pro);

        order = new PaymentOrder();
        order.setPaymentOrderId(UUID.randomUUID());
        order.setVnpTxnRef("PRO-20260805-settlement");
        order.setUserId(userId);
        order.setPlanId(planId);
        order.setPlanCodeSnapshot("PRO");
        order.setGateway("VNPAY");
        order.setStatus("PENDING");
        order.setAmountVnd(49_000L);
        order.setDurationDays(30);
        order.setExpiresAt(LocalDateTime.of(2026, 8, 5, 10, 15));
        when(orders.findForUpdateByVnpTxnRef(order.getVnpTxnRef())).thenReturn(Optional.of(order));
        when(orders.save(any(PaymentOrder.class))).thenAnswer(call -> call.getArgument(0));
        when(subscriptions.save(any(UserSubscription.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void successfulIpnActivatesExactlyThirtyDaysAndCreatesOneHistoryEvent() {
        when(subscriptions.findForUpdateByUserId(userId)).thenReturn(Optional.empty());
        SubscriptionPlan free = new SubscriptionPlan();
        free.setPlanId(UUID.randomUUID());
        when(plans.requireActive("FREE")).thenReturn(free);

        VnpaySettlementService.SettlementResult result = settlement.settle(successParams());

        assertEquals("00", result.rspCode());
        assertEquals("PAID", order.getStatus());
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(subscriptions).save(subscriptionCaptor.capture());
        UserSubscription saved = subscriptionCaptor.getValue();
        assertEquals(LocalDateTime.of(2026, 8, 5, 10, 0), saved.getStartedAt());
        assertEquals(LocalDateTime.of(2026, 9, 4, 10, 0), saved.getExpiresAt());
        ArgumentCaptor<SubscriptionHistory> historyCaptor = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(history).save(historyCaptor.capture());
        assertEquals(30, historyCaptor.getValue().getDaysAdded());
        assertEquals(saved.getExpiresAt(), historyCaptor.getValue().getExtensionTo());
    }

    @Test
    void activePaidPlanIsNeverExtendedOrChangedByAnotherOrder() {
        UserSubscription current = new UserSubscription();
        current.setUserId(userId);
        current.setPlanId(planId);
        current.setStatus("PRO_ACTIVE");
        current.setStartedAt(LocalDateTime.of(2026, 7, 1, 8, 0));
        current.setExpiresAt(LocalDateTime.of(2026, 8, 20, 8, 0));
        when(subscriptions.findForUpdateByUserId(userId)).thenReturn(Optional.of(current));

        VnpaySettlementService.SettlementResult first = settlement.settle(successParams());
        VnpaySettlementService.SettlementResult duplicate = settlement.settle(successParams());

        assertEquals(LocalDateTime.of(2026, 8, 20, 8, 0), current.getExpiresAt());
        assertEquals("FAILED", order.getStatus());
        assertEquals("ACTIVE_SUBSCRIPTION_EXISTS", first.validationError());
        assertEquals("02", duplicate.rspCode());
        verify(history, never()).save(any(SubscriptionHistory.class));
        verify(subscriptions, never()).save(any(UserSubscription.class));
    }

    @Test
    void wrongAmountNeverActivatesSubscription() {
        Map<String, String> params = successParams();
        params.put("vnp_Amount", "4900100");

        VnpaySettlementService.SettlementResult result = settlement.settle(params);

        assertEquals("04", result.rspCode());
        assertEquals("PENDING", order.getStatus());
        assertEquals(Boolean.FALSE, result.amountValid());
        assertNull(order.getPaidAt());
        verify(subscriptions, never()).save(any(UserSubscription.class));
        verify(history, never()).save(any(SubscriptionHistory.class));
    }

    private Map<String, String> successParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", order.getVnpTxnRef());
        params.put("vnp_Amount", "4900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_PayDate", "20260805100000");
        params.put("vnp_BankCode", "NCB");
        return params;
    }
}
