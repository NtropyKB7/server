package com.ntropy.payment.client;

import com.ntropy.common.client.SubscriptionQueryClient;
import com.ntropy.common.dto.PlanSummary;
import com.ntropy.common.dto.SubscriptionSummary;
import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * common.client.SubscriptionQueryClient 구현체.
 * bff-service는 이 인터페이스 타입으로만 주입받고, 실제 구현은 여기(payment-service)에 있다.
 * SubscriptionService(실제 로직) 결과를 common 모듈의 DTO로 변환하는 어댑터 역할만 한다.
 */
@Component
public class LocalSubscriptionQueryClient implements SubscriptionQueryClient {

    private final SubscriptionService subscriptionService;

    @Autowired
    public LocalSubscriptionQueryClient(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public List<PlanSummary> getPlans() {
        return subscriptionService.getAllPlans().stream()
                .map(this::toPlanSummary)
                .collect(Collectors.toList());
    }

    @Override
    public SubscriptionSummary getMySubscription(Long userId) {
        return toSubscriptionSummary(subscriptionService.getMySubscription(userId));
    }

    private PlanSummary toPlanSummary(PlanCode planCode) {
        return new PlanSummary(
                planCode.name(),
                planCode.getDisplayName(),
                planCode.getMonthlyPrice(),
                planCode.getFeatureLabels()
        );
    }

    private SubscriptionSummary toSubscriptionSummary(Subscription s) {
        return new SubscriptionSummary(
                s.getSubscriptionId(),
                s.getPlanCode() != null ? s.getPlanCode().name() : null,
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getStartDate(),
                s.getEndDate(),
                s.getAutoRenewYn(),
                s.getCancelRequestedAt(),
                s.getCardName(),
                s.getCardNumberMasked()
        );
    }
}