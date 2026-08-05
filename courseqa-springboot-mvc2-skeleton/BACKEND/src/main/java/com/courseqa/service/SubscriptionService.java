package com.courseqa.service;

import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.SubscriptionHistory;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.model.entity.UserSubscription;
import com.courseqa.repository.SubscriptionHistoryRepository;
import com.courseqa.repository.UserSubscriptionRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final UserSubscriptionRepository subscriptions;
    private final SubscriptionHistoryRepository history;
    private final PlanService planService;

    public SubscriptionService(
            UserSubscriptionRepository subscriptions,
            SubscriptionHistoryRepository history,
            PlanService planService
    ) {
        this.subscriptions = subscriptions;
        this.history = history;
        this.planService = planService;
    }

    @Transactional
    public UserSubscription ensureFreeSubscription(UUID userId) {
        return subscriptions.findByUserId(userId).orElseGet(() -> {
            SubscriptionPlan free = planService.requireActive("FREE");
            UserSubscription subscription = new UserSubscription();
            subscription.setUserId(userId);
            subscription.setPlanId(free.getPlanId());
            subscription.setStatus("FREE");
            subscription.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
            return subscriptions.save(subscription);
        });
    }

    @Transactional
    public SubscriptionPlan effectivePlan(UUID userId) {
        EffectiveSubscription effective = resolve(userId, LocalDateTime.now(BUSINESS_ZONE));
        return effective.plan();
    }

    @Transactional
    public SubscriptionPlan effectivePlanForQuota(UUID userId) {
        UserSubscription subscription = subscriptions.findForUpdateByUserId(userId)
                .orElseGet(() -> ensureFreeSubscription(userId));
        return resolveExisting(subscription, LocalDateTime.now(BUSINESS_ZONE)).plan();
    }

    @Transactional
    public PaymentDto.SubscriptionResponse current(UUID userId) {
        EffectiveSubscription effective = resolve(userId, LocalDateTime.now(BUSINESS_ZONE));
        UserSubscription subscription = effective.subscription();
        PaymentDto.SubscriptionResponse response = new PaymentDto.SubscriptionResponse();
        response.status = subscription.getStatus();
        response.effectivePlanCode = effective.plan().getPlanCode();
        response.startedAt = subscription.getStartedAt();
        response.expiresAt = subscription.getExpiresAt();
        response.plan = planService.toResponse(effective.plan());
        return response;
    }

    @Transactional
    public void assertPurchaseAllowed(UUID userId) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        UserSubscription subscription = subscriptions.findForUpdateByUserId(userId)
                .orElseGet(() -> ensureFreeSubscription(userId));
        boolean activePaidPlan = "PRO_ACTIVE".equals(subscription.getStatus())
                && subscription.getExpiresAt() != null
                && subscription.getExpiresAt().isAfter(now);
        if (activePaidPlan) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A paid plan is already active until " + subscription.getExpiresAt()
                            + ". You can purchase another plan only after it expires.");
        }
        if ("PRO_ACTIVE".equals(subscription.getStatus())) {
            subscription.setStatus("PRO_EXPIRED");
            subscription.setUpdatedAt(now);
            subscriptions.save(subscription);
        }
    }

    public List<PaymentDto.SubscriptionHistoryResponse> history(UUID userId) {
        return history.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    private EffectiveSubscription resolve(UUID userId, LocalDateTime now) {
        UserSubscription subscription = ensureFreeSubscription(userId);
        return resolveExisting(subscription, now);
    }

    private EffectiveSubscription resolveExisting(UserSubscription subscription, LocalDateTime now) {
        boolean activePro = "PRO_ACTIVE".equals(subscription.getStatus())
                && subscription.getExpiresAt() != null
                && subscription.getExpiresAt().isAfter(now);
        if (activePro) {
            return new EffectiveSubscription(subscription, planService.requireById(subscription.getPlanId()));
        }
        if ("PRO_ACTIVE".equals(subscription.getStatus())) {
            subscription.setStatus("PRO_EXPIRED");
            subscription.setUpdatedAt(now);
            subscriptions.save(subscription);
        }
        return new EffectiveSubscription(subscription, planService.requireActive("FREE"));
    }

    private PaymentDto.SubscriptionHistoryResponse toResponse(SubscriptionHistory item) {
        PaymentDto.SubscriptionHistoryResponse response = new PaymentDto.SubscriptionHistoryResponse();
        response.historyId = item.getSubscriptionHistoryId();
        response.orderId = item.getPaymentOrderId();
        response.planCode = planService.requireById(item.getPlanId()).getPlanCode();
        response.extensionFrom = item.getExtensionFrom();
        response.extensionTo = item.getExtensionTo();
        response.daysAdded = item.getDaysAdded();
        response.amountVnd = item.getAmountVnd();
        response.paidAt = item.getPaidAt();
        return response;
    }

    private record EffectiveSubscription(UserSubscription subscription, SubscriptionPlan plan) { }
}
