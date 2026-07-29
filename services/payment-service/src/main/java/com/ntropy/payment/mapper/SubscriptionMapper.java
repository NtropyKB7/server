package com.ntropy.payment.mapper;

import com.ntropy.payment.domain.Subscription;

import java.util.List;

public interface SubscriptionMapper {

    Subscription findById(Long subscriptionId);

    Subscription findLatestByUserId(Long userId);

    Subscription findByCustomerUid(String customerUid);

    List<Subscription> findAllByUserId(Long userId);

    int insert(Subscription subscription);

    int update(Subscription subscription);
}