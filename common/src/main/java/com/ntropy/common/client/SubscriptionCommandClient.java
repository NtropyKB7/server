package com.ntropy.common.client;

import com.ntropy.common.dto.SubscriptionSummary;

/**
 * 구독 상태를 변경하는(쓰기) 작업 전용 인터페이스.
 * SubscriptionQueryClient(읽기 전용)와 의도적으로 분리했다 (CQS 원칙) -
 * "Query"라는 이름의 인터페이스에 쓰기 메서드가 섞이면 나중에 헷갈리기 때문.
 */
public interface SubscriptionCommandClient {

    /**
     * 최초결제 + 빌링키 발급을 검증하고 구독을 생성한다.
     * paymentId로 포트원 서버에 실제로 결제가 완료됐는지 검증하고, 클라이언트가 보낸
     * amount는 검증된 값과 대조만 할 뿐 그대로 신뢰하지 않는다.
     *
     * paymentId 하나만 받는 이유 (V1 imp_uid+merchant_uid 두 개가 아님):
     * 포트원 V2는 프론트가 결제 요청 시 직접 지정하는 paymentId 하나가
     * "우리 주문번호"이자 "포트원 조회 키" 역할을 겸한다.
     *
     * @param userId 로그인한 유저 ID
     * @param paymentId 포트원 V2 결제 식별자 (검증의 기준이자 중복결제 방지 키)
     * @param customerUid 포트원 빌링키 식별자 (회원ID 매핑용, 우리가 지정한 값)
     * @param amount 클라이언트가 주장하는 결제금액 (검증 응답의 실제 금액과 대조만 함)
     */
    SubscriptionSummary initSubscription(Long userId, String paymentId, String customerUid, Long amount);
}