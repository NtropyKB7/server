package com.ntropy.payment.service;

import com.ntropy.common.exception.ServiceException;
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

/**
 * 구독/결제 비즈니스 로직.
 * Issue #5(정기결제)부터 순차적으로 더 채워진다.
 */
@Service
public class SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final PaymentMapper paymentMapper;
    private final PortOnePaymentClient portOnePaymentClient;

    @Autowired
    public SubscriptionService(SubscriptionMapper subscriptionMapper,
                               PaymentMapper paymentMapper,
                               PortOnePaymentClient portOnePaymentClient) {
        this.subscriptionMapper = subscriptionMapper;
        this.paymentMapper = paymentMapper;
        this.portOnePaymentClient = portOnePaymentClient;
    }

    /** 서비스가 제공하는 전체 플랜 목록. 유저와 무관하게 고정된 값이라 DB 조회가 필요 없다. */
    public List<PlanCode> getAllPlans() {
        return Arrays.asList(PlanCode.values());
    }

    /**
     * 유저의 현재 구독 상태를 조회한다.
     * SUBSCRIPTION 테이블에 한 번도 구독한 적 없는 유저는 행 자체가 없을 수 있는데,
     * 이 경우는 에러가 아니라 "무료 Basic을 쓰고 있는 상태"로 간주해 기본값을 만들어 돌려준다.
     */
    public Subscription getMySubscription(Long userId) {
        Subscription subscription = subscriptionMapper.findLatestByUserId(userId);
        return subscription != null ? subscription : defaultBasicSubscription();
    }

    /**
     * 최초결제 + 빌링키 발급을 검증하고 PRO 구독을 생성한다.
     *
     * 순서가 중요하다:
     * 1) paymentId 중복 체크 (SUBSCRIPTION02_F05) - DB UNIQUE 제약(merchant_uid 컬럼)이
     *    최후 방어선이지만, 그 전에 명확한 에러 메시지로 먼저 걸러낸다.
     * 2) 포트원 서버에 실제로 검증 요청 - 클라이언트가 보낸 값은 여기서부터 신뢰하지 않는다.
     * 3) 검증된 실제 결제금액과 클라이언트 요청값을 대조 - 다르면 위변조 의심.
     * 4) SUBSCRIPTION + PAYMENT 저장 - 전부 검증된 값 기준으로만 채운다.
     *
     * ⚠️ paymentId는 PAYMENT.merchant_uid 컬럼에 저장한다 (컬럼명은 V1 시절 이름 그대로
     * 유지 - PAYMENT.payment_id는 이미 우리 자체 PK로 쓰고 있어서 이름 충돌 방지 목적도 있음).
     */
    @Transactional
    public Subscription initSubscription(Long userId, String paymentId, String customerUid, Long claimedAmount) {
        if (paymentMapper.findByMerchantUid(paymentId) != null) {
            throw new ServiceException(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        PortOnePaymentVerification verification = portOnePaymentClient.verifyPayment(paymentId);

        if (!verification.isPaid()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_NOT_COMPLETED);
        }
        if (verification.getAmount() != PlanCode.PRO.getMonthlyPrice()) {
            throw new ServiceException(PaymentErrorCode.AMOUNT_MISMATCH);
        }

        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanCode(PlanCode.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(1));
        subscription.setAutoRenewYn(true);
        subscription.setCustomerUid(customerUid);
        subscription.setPaymentMethod(verification.getPaymentMethod());
        subscription.setPaymentLabel(verification.getPaymentLabel());
        subscription.setPaymentMasked(verification.getPaymentMasked());
        subscriptionMapper.insert(subscription);

        Payment payment = new Payment();
        payment.setSubscriptionId(subscription.getSubscriptionId());
        payment.setPlanCode(PlanCode.PRO);
        payment.setAmount(verification.getAmount());
        payment.setPaymentMethod(verification.getPaymentMethod());
        payment.setCreatedAt(now);
        payment.setMerchantUid(paymentId);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setReceiptUrl(verification.getReceiptUrl());
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
}