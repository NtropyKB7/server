package com.ntropy.bff.dto.subscription.response;

import com.ntropy.common.dto.payment.PlanSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionManagementResponse {

    private SubscriptionResponse currentSubscription;
    private List<PlanSummary> availablePlans;
}