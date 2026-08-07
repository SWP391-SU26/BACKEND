package com.courseqa.service;

import com.courseqa.model.dto.PaymentDto;
import com.courseqa.model.entity.SubscriptionPlan;
import com.courseqa.repository.SubscriptionPlanRepository;
import com.courseqa.repository.PaymentOrderRepository;
import com.courseqa.repository.SubscriptionHistoryRepository;
import com.courseqa.repository.UserSubscriptionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanService {
    private final SubscriptionPlanRepository plans;
    private final ObjectMapper objectMapper;
    private final PaymentOrderRepository orders;
    private final UserSubscriptionRepository subscriptions;
    private final SubscriptionHistoryRepository history;

    public PlanService(
            SubscriptionPlanRepository plans,
            ObjectMapper objectMapper,
            PaymentOrderRepository orders,
            UserSubscriptionRepository subscriptions,
            SubscriptionHistoryRepository history
    ) {
        this.plans = plans;
        this.objectMapper = objectMapper;
        this.orders = orders;
        this.subscriptions = subscriptions;
        this.history = history;
    }

    public List<PaymentDto.PlanResponse> getActivePlans() {
        return plans.findByIsActiveTrueOrderByPriceVndAsc().stream().map(this::toResponse).toList();
    }

    public List<PaymentDto.PlanResponse> getAllPlans() {
        return plans.findAllByOrderByPriceVndAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PaymentDto.PlanResponse create(PaymentDto.PlanUpsertRequest request) {
        String code = normalizeCode(request.planCode);
        if (plans.existsByPlanCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plan code already exists.");
        }
        validateBusinessRules(code, request);
        LocalDateTime now = LocalDateTime.now();
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode(code);
        apply(plan, request);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        return toResponse(plans.save(plan));
    }

    @Transactional
    public PaymentDto.PlanResponse update(UUID planId, PaymentDto.PlanUpsertRequest request) {
        SubscriptionPlan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found."));
        String code = normalizeCode(request.planCode);
        if (!plan.getPlanCode().equalsIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plan code cannot be changed after creation.");
        }
        validateBusinessRules(code, request);
        apply(plan, request);
        plan.setUpdatedAt(LocalDateTime.now());
        return toResponse(plans.save(plan));
    }

    @Transactional
    public PaymentDto.PlanDeleteResponse delete(UUID planId) {
        SubscriptionPlan plan = plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found."));
        if ("FREE".equalsIgnoreCase(plan.getPlanCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The FREE fallback plan cannot be deleted or deactivated.");
        }
        boolean referenced = orders.existsByPlanId(planId)
                || subscriptions.existsByPlanId(planId)
                || history.existsByPlanId(planId);
        if (referenced) {
            plan.setIsActive(false);
            plan.setUpdatedAt(LocalDateTime.now());
            plans.save(plan);
            return new PaymentDto.PlanDeleteResponse(false, true,
                    "Plan is already in use, so it was deactivated instead of deleted.");
        }
        plans.delete(plan);
        return new PaymentDto.PlanDeleteResponse(true, false, "Plan deleted.");
    }

    public SubscriptionPlan requireActive(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return plans.findByPlanCodeAndIsActiveTrue(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found."));
    }

    public SubscriptionPlan requireById(java.util.UUID planId) {
        return plans.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Subscription plan is missing."));
    }

    public PaymentDto.PlanResponse toResponse(SubscriptionPlan plan) {
        PaymentDto.PlanResponse response = new PaymentDto.PlanResponse();
        response.planId = plan.getPlanId();
        response.planCode = plan.getPlanCode();
        response.displayName = plan.getDisplayName();
        response.priceVnd = plan.getPriceVnd();
        response.durationDays = plan.getDurationDays();
        response.maxFileBytes = plan.getMaxFileBytes();
        response.maxDocuments = plan.getMaxDocuments();
        response.maxStorageBytes = plan.getMaxStorageBytes();
        response.maxPersonalWorkspaces = plan.getMaxPersonalWorkspaces();
        try {
            response.benefits = objectMapper.readValue(plan.getBenefitsJson(), new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
        response.benefits = List.of();
        }
        response.isActive = Boolean.TRUE.equals(plan.getIsActive());
        response.createdAt = plan.getCreatedAt();
        response.updatedAt = plan.getUpdatedAt();
        return response;
    }

    private void apply(SubscriptionPlan plan, PaymentDto.PlanUpsertRequest request) {
        plan.setDisplayName(request.displayName.trim());
        plan.setPriceVnd(request.priceVnd);
        plan.setDurationDays(request.durationDays);
        plan.setMaxFileBytes(request.maxFileBytes);
        plan.setMaxDocuments(request.maxDocuments);
        plan.setMaxStorageBytes(request.maxStorageBytes);
        plan.setMaxPersonalWorkspaces(request.maxPersonalWorkspaces);
        plan.setIsActive(request.isActive == null || request.isActive);
        try {
            List<String> benefits = request.benefits.stream().map(String::trim).filter(item -> !item.isBlank()).toList();
            plan.setBenefitsJson(objectMapper.writeValueAsString(benefits));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan benefits are invalid.");
        }
    }

    private static void validateBusinessRules(String code, PaymentDto.PlanUpsertRequest request) {
        boolean active = request.isActive == null || request.isActive;
        if (request.maxStorageBytes < request.maxFileBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total storage must be greater than or equal to the per-file limit.");
        }
        if (request.priceVnd > 0 && request.durationDays == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid plans require a duration.");
        }
        if (request.priceVnd == 0 && request.durationDays != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Free plans must not expire.");
        }
        if ("FREE".equals(code) && (request.priceVnd != 0 || request.durationDays != null || !active)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The FREE fallback plan must remain active, free, and without an expiry.");
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
