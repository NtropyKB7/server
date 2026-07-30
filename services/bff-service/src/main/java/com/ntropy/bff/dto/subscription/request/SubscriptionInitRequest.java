package com.ntropy.bff.dto.subscription.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /api/subscriptions 요청 바디.
 * {paymentId, customerUid, amount} 그대로 매핑한다.
 *
 * ⚠️ 원래 API 명세는 V1 스타일(impUid + merchantUid 2개)이었는데, 실제 검증 호출을
 * V2로 구현하면서 하나로 합쳤다 (V2는 프론트가 지정하는 paymentId 하나가 "우리
 * 주문번호"이자 "포트원 조회 키"를 겸함).
 *
 * ⚠️ amount는 여기 그대로 있지만 서버가 이 값을 그대로 믿지 않는다 -
 * SubscriptionService가 포트원 검증 응답의 실제 결제금액과 대조한 뒤에만 통과시킨다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionInitRequest {

    private String paymentId;
    private String customerUid;
    private Long amount;
}