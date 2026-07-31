package com.ntropy.common.client;

import com.ntropy.common.dto.SubscriptionSummary;

public interface SubscriptionCommandClient {

    SubscriptionSummary initSubscription(Long userId, String billingKey);
    SubscriptionSummary updatePaymentMethod(Long userId, String billingKey);
    void handleScheduledPaymentResult(String paymentId);
    boolean receiveWebhook(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody);
}