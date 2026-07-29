package com.ntropy.payment.service;

import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.domain.SubscriptionStatus;
import com.ntropy.payment.mapper.SubscriptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    @Autowired
    public SubscriptionService(SubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }


    public List<PlanCode> getAllPlans() {
        return Arrays.asList(PlanCode.values());
    }


    public Subscription getMySubscription(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        return subscription != null ? subscription : defaultBasicSubscription();
    }

    private Subscription defaultBasicSubscription() {
        Subscription basic = new Subscription();
        basic.setPlanCode(PlanCode.BASIC);
        basic.setStatus(SubscriptionStatus.ACTIVE);
        basic.setAutoRenewYn(false);
        return basic;
    }
}