package com.ntropy.common.client;

import com.ntropy.common.dto.SubscriptionSummary;

public interface SubscriptionCommandClient {

    SubscriptionSummary initSubscription(Long userId, String billingKey);
    SubscriptionSummary updatePaymentMethod(Long userId, String billingKey);
}