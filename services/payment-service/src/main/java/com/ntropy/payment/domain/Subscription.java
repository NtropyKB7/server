package com.ntropy.payment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    private Long subscriptionId;
    private Long userId;
    private PlanCode planCode;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime cancelRequestedAt;
    private Boolean autoRenewYn;
    private String customerUid;
    private String cardName;
    private String cardNumberMasked;
    private SubscriptionStatus status;

    public boolean isUsable() {
        return status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCEL_SCHEDULED;
    }

    public boolean supports(Feature feature) {
        return isUsable() && planCode != null && planCode.supports(feature);
    }
}