package com.ntropy.payment.client;

import com.ntropy.common.client.SubscriptionCommandClient;
import com.ntropy.common.client.SubscriptionQueryClient;
import com.ntropy.common.domain.Feature;
import com.ntropy.common.dto.payment.PlanSummary;
import com.ntropy.common.dto.payment.SubscriptionSummary;
import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LocalSubscriptionQueryClient implements SubscriptionQueryClient, SubscriptionCommandClient {

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

    @Override
    public boolean supportsFeature(Long userId, Feature feature) {
        Subscription subscription = subscriptionService.getMySubscription(userId);
        return subscription.supports(feature);
    }

    @Override
    public SubscriptionSummary initSubscription(Long userId, String billingKey) {
        Subscription subscription = subscriptionService.initSubscription(userId, billingKey);
        return toSubscriptionSummary(subscription);
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
                s.getPaymentMethod() != null ? s.getPaymentMethod().name() : null,
                s.getPaymentLabel(),
                s.getPaymentMasked()
        );
    }

    @Override
    public SubscriptionSummary updatePaymentMethod(Long userId, String billingKey) {
        Subscription subscription = subscriptionService.updatePaymentMethod(userId, billingKey);
        return toSubscriptionSummary(subscription);
    }

    @Override
    public void handleScheduledPaymentResult(String paymentId) {
        subscriptionService.handleScheduledPaymentResult(paymentId);
    }


    @Override
    public boolean receiveWebhook(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody) {
        return subscriptionService.receiveWebhook(webhookId, webhookTimestamp, webhookSignature, rawBody);
    }
}