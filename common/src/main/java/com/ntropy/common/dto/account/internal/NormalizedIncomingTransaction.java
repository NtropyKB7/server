package com.ntropy.common.dto.account.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 은행별 원본 거래를 서비스 간 전달용 공통 입금 거래 구조로 가공한 결과.
 * counterpartyName은 은행별 필드 선택만 끝낸 값이며 접두사 제거와 비교 정규화는 적용하지 않는다.
 */
public record NormalizedIncomingTransaction(
        Long transactionId,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String counterpartyName,
        BigDecimal amount
) {
}
