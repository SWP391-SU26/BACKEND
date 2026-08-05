package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courseqa.model.entity.UserSubscription;
import com.courseqa.repository.SubscriptionHistoryRepository;
import com.courseqa.repository.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SubscriptionServiceTest {
    private UserSubscriptionRepository subscriptions;
    private SubscriptionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        subscriptions = mock(UserSubscriptionRepository.class);
        service = new SubscriptionService(
                subscriptions,
                mock(SubscriptionHistoryRepository.class),
                mock(PlanService.class)
        );
        userId = UUID.randomUUID();
    }

    @Test
    void activePaidPlanBlocksRenewalAndSwitchingPlans() {
        UserSubscription current = subscription(LocalDateTime.now().plusDays(20));
        when(subscriptions.findForUpdateByUserId(userId)).thenReturn(Optional.of(current));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.assertPurchaseAllowed(userId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("PRO_ACTIVE", current.getStatus());
        verify(subscriptions, never()).save(any(UserSubscription.class));
    }

    @Test
    void expiredPaidPlanCanPurchaseAgain() {
        UserSubscription current = subscription(LocalDateTime.now().minusSeconds(1));
        when(subscriptions.findForUpdateByUserId(userId)).thenReturn(Optional.of(current));
        when(subscriptions.save(any(UserSubscription.class))).thenAnswer(call -> call.getArgument(0));

        service.assertPurchaseAllowed(userId);

        assertEquals("PRO_EXPIRED", current.getStatus());
        verify(subscriptions).save(current);
    }

    private UserSubscription subscription(LocalDateTime expiresAt) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setStatus("PRO_ACTIVE");
        subscription.setExpiresAt(expiresAt);
        return subscription;
    }
}
