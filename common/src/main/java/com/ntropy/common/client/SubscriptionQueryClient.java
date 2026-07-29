package com.ntropy.common.client;

import com.ntropy.common.dto.PlanSummary;
import com.ntropy.common.dto.SubscriptionSummary;

import java.util.List;

public interface SubscriptionQueryClient {

    List<PlanSummary> getPlans();

    SubscriptionSummary getMySubscription(Long userId);
}