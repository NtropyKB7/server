package com.ntropy.common.client;

import com.ntropy.common.dto.payment.PlanSummary;
import com.ntropy.common.dto.payment.SubscriptionSummary;

import java.util.List;

public interface SubscriptionQueryClient {

    List<PlanSummary> getPlans();

    SubscriptionSummary getMySubscription(Long userId);
}