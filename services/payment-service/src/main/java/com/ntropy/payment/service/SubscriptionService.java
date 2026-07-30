package com.ntropy.payment.service;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.payment.client.portone.PortOneBillingKeyClient;
import com.ntropy.payment.client.portone.PortOneBillingKeyVerification;
import com.ntropy.payment.client.portone.PortOnePaymentClient;
import com.ntropy.payment.client.portone.PortOnePaymentVerification;
import com.ntropy.payment.domain.Payment;
import com.ntropy.payment.domain.PaymentStatus;
import com.ntropy.payment.domain.PlanCode;
import com.ntropy.payment.domain.Subscription;
import com.ntropy.payment.domain.SubscriptionStatus;
import com.ntropy.payment.exception.PaymentErrorCode;
import com.ntropy.payment.mapper.PaymentMapper;
import com.ntropy.payment.mapper.SubscriptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// 구독/결제 비즈니스 로직.

@Service
public class SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final PaymentMapper paymentMapper;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PortOneBillingKeyClient portOneBillingKeyClient;
    @Autowired
    public SubscriptionService(SubscriptionMapper subscriptionMapper,
                               PaymentMapper paymentMapper,
                               PortOnePaymentClient portOnePaymentClient,
                               PortOneBillingKeyClient portOneBillingKeyClient) {  // ← 생성자 파라미터에도 있나요?
        this.subscriptionMapper = subscriptionMapper;
        this.paymentMapper = paymentMapper;
        this.portOnePaymentClient = portOnePaymentClient;
        this.portOneBillingKeyClient = portOneBillingKeyClient;   // ← 이 대입문 있나요?
    }
    /** 서비스가 제공하는 전체 플랜 목록. 유저와 무관하게 고정된 값이라 DB 조회가 필요 없다. */
    public List<PlanCode> getAllPlans() {
        return Arrays.asList(PlanCode.values());
    }

    // 유저의 현재 구독 상태를 조회한다.

    public Subscription getMySubscription(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        return subscription != null ? subscription : defaultBasicSubscription();
    }


    // 최초결제 + 빌링키 발급을 검증하고 PRO 구독을 생성한다.

    @Transactional
    public Subscription initSubscription(Long userId, String billingKey) {
        Subscription existing = subscriptionMapper.findLatestByUserId(userId);
        if (existing != null && existing.isUsable()) {
            throw new ServiceException(PaymentErrorCode.ALREADY_SUBSCRIBED);
        }

        PortOneBillingKeyVerification billingKeyVerification = portOneBillingKeyClient.verifyBillingKey(billingKey);
        if (!billingKeyVerification.isValid()) {
            throw new ServiceException(PaymentErrorCode.INVALID_BILLING_KEY);
        }

        String paymentId = "sub-init-" + UUID.randomUUID();
        PortOnePaymentVerification paymentResult = portOnePaymentClient.payWithBillingKey(
                paymentId, billingKey, PlanCode.PRO.getMonthlyPrice(), "Ntropy Pro 구독 최초결제");

        if (!paymentResult.isPaid()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_NOT_COMPLETED);
        }

        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanCode(PlanCode.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(1));
        subscription.setAutoRenewYn(true);
        subscription.setCustomerUid(billingKey);
        subscription.setPaymentMethod(paymentResult.getPaymentMethod());
        subscription.setPaymentLabel(paymentResult.getPaymentLabel());
        subscription.setPaymentMasked(paymentResult.getPaymentMasked());
        subscriptionMapper.insert(subscription);

        Payment payment = new Payment();
        payment.setSubscriptionId(subscription.getSubscriptionId());
        payment.setPlanCode(PlanCode.PRO);
        payment.setAmount(paymentResult.getAmount());
        payment.setPaymentMethod(paymentResult.getPaymentMethod());
        payment.setCreatedAt(now);
        payment.setMerchantUid(paymentId);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setReceiptUrl(paymentResult.getReceiptUrl());
        paymentMapper.insert(payment);

        return subscription;
    }

    private Subscription defaultBasicSubscription() {
        Subscription basic = new Subscription();
        basic.setPlanCode(PlanCode.BASIC);
        basic.setStatus(SubscriptionStatus.ACTIVE);
        basic.setAutoRenewYn(false);
        return basic;
    }

    @Transactional
    public Subscription updatePaymentMethod(Long userId, String billingKey) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        if (subscription == null) {
            throw new ServiceException(PaymentErrorCode.SUBSCRIPTION_NOT_FOUND);
        }

        PortOneBillingKeyVerification verification = portOneBillingKeyClient.verifyBillingKey(billingKey);
        if (!verification.isValid()) {
            throw new ServiceException(PaymentErrorCode.INVALID_BILLING_KEY);
        }

        subscription.setCustomerUid(billingKey);
        subscription.setPaymentMethod(verification.getPaymentMethod());
        subscription.setPaymentLabel(verification.getPaymentLabel());
        subscription.setPaymentMasked(verification.getPaymentMasked());
        subscriptionMapper.update(subscription);

        return subscription;
    }
}