package com.ntropy.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.payment.client.portone.PortOneBillingKeyClient;
import com.ntropy.payment.client.portone.PortOneBillingKeyVerification;
import com.ntropy.payment.client.portone.PortOnePaymentClient;
import com.ntropy.payment.client.portone.PortOnePaymentVerification;
import com.ntropy.payment.client.portone.PortOneWebhookVerifier;
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

@Service
public class SubscriptionService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL_DAYS = 1;

    private final SubscriptionMapper subscriptionMapper;
    private final PaymentMapper paymentMapper;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PortOneBillingKeyClient portOneBillingKeyClient;
    private final PortOneWebhookVerifier portOneWebhookVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SubscriptionService(SubscriptionMapper subscriptionMapper,
                               PaymentMapper paymentMapper,
                               PortOnePaymentClient portOnePaymentClient,
                               PortOneBillingKeyClient portOneBillingKeyClient,
                               PortOneWebhookVerifier portOneWebhookVerifier) {
        this.subscriptionMapper = subscriptionMapper;
        this.paymentMapper = paymentMapper;
        this.portOnePaymentClient = portOnePaymentClient;
        this.portOneBillingKeyClient = portOneBillingKeyClient;
        this.portOneWebhookVerifier = portOneWebhookVerifier;
    }

    public List<PlanCode> getAllPlans() {
        return Arrays.asList(PlanCode.values());
    }

    public Subscription getMySubscription(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        return subscription != null ? subscription : defaultBasicSubscription();
    }

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

        scheduleUpcomingPayment(subscription, subscription.getEndDate());

        return subscription;
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

    @Transactional
    public void handleScheduledPaymentResult(String paymentId) {
        Payment pendingPayment = paymentMapper.findByMerchantUid(paymentId);
        if (pendingPayment == null) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        if (pendingPayment.getPaymentStatus() != PaymentStatus.PENDING) {
            // 이미 SUCCESS/FAILED로 처리된 건 - 웹훅 중복수신. 재처리하지 않고 그대로 종료.
            return;
        }

        PortOnePaymentVerification result = portOnePaymentClient.verifyPayment(paymentId);

        if (result.isPaid()) {
            handleScheduledPaymentSuccess(pendingPayment, result);
        } else {
            handleScheduledPaymentFailure(pendingPayment);
        }
    }

    @Transactional
    public boolean receiveWebhook(String webhookId, String webhookTimestamp, String webhookSignature, String rawBody) {
        if (!portOneWebhookVerifier.verify(webhookId, webhookTimestamp, webhookSignature, rawBody)) {
            return false;
        }

        String paymentId = extractPaymentId(rawBody);
        if (paymentId != null && paymentMapper.findByMerchantUid(paymentId) != null) {
            handleScheduledPaymentResult(paymentId);
        }

        return true;
    }

    private String extractPaymentId(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode paymentIdNode = root.path("data").path("paymentId");
            return paymentIdNode.isMissingNode() ? null : paymentIdNode.asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleScheduledPaymentSuccess(Payment pendingPayment, PortOnePaymentVerification result) {
        pendingPayment.setAmount(result.getAmount());
        pendingPayment.setPaymentMethod(result.getPaymentMethod());
        pendingPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        pendingPayment.setFailureReason(null);
        pendingPayment.setReceiptUrl(result.getReceiptUrl());
        paymentMapper.update(pendingPayment);

        Subscription subscription = subscriptionMapper.findById(pendingPayment.getSubscriptionId());
        subscription.setEndDate(subscription.getEndDate().plusMonths(1));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setPaymentMethod(result.getPaymentMethod());
        subscription.setPaymentLabel(result.getPaymentLabel());
        subscription.setPaymentMasked(result.getPaymentMasked());
        subscriptionMapper.update(subscription);

        scheduleUpcomingPayment(subscription, subscription.getEndDate());
    }

    private void handleScheduledPaymentFailure(Payment pendingPayment) {
        pendingPayment.setPaymentStatus(PaymentStatus.FAILED);
        pendingPayment.setFailureReason("포트원 결제 실패");
        paymentMapper.update(pendingPayment);

        Subscription subscription = subscriptionMapper.findById(pendingPayment.getSubscriptionId());
        int consecutiveFailures = countConsecutiveFailures(subscription.getSubscriptionId());

        if (consecutiveFailures < MAX_RETRY_COUNT) {
            scheduleUpcomingPayment(subscription, LocalDateTime.now().plusDays(RETRY_INTERVAL_DAYS));
        } else {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionMapper.update(subscription);
            // TODO(notification-service 연계 지점): 정기결제 최종 실패로 구독이 만료됐다는 알림 발송
        }
    }

    private int countConsecutiveFailures(Long subscriptionId) {
        List<Payment> history = paymentMapper.findAllBySubscriptionId(subscriptionId);
        int count = 0;
        for (Payment payment : history) {
            if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private void scheduleUpcomingPayment(Subscription subscription, LocalDateTime timeToPay) {
        String paymentId = "sub-recurring-" + subscription.getSubscriptionId() + "-" + UUID.randomUUID();

        Payment pendingPayment = new Payment();
        pendingPayment.setSubscriptionId(subscription.getSubscriptionId());
        pendingPayment.setPlanCode(PlanCode.PRO);
        pendingPayment.setAmount((long) PlanCode.PRO.getMonthlyPrice());
        pendingPayment.setCreatedAt(LocalDateTime.now());
        pendingPayment.setMerchantUid(paymentId);
        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        paymentMapper.insert(pendingPayment);

        portOnePaymentClient.schedulePayment(
                paymentId, subscription.getCustomerUid(), PlanCode.PRO.getMonthlyPrice(),
                "Ntropy Pro 정기결제", timeToPay);
    }

    private Subscription defaultBasicSubscription() {
        Subscription basic = new Subscription();
        basic.setPlanCode(PlanCode.BASIC);
        basic.setStatus(SubscriptionStatus.ACTIVE);
        basic.setAutoRenewYn(false);
        return basic;
    }
}