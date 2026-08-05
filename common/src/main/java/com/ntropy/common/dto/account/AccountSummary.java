package com.ntropy.common.dto.account;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 외부 조회 계약에 노출하는 계좌 정보. 내부 연결 ID와 계좌번호 해시는 포함하지 않는다. */
public record AccountSummary(
        Long accountId,
        String organizationCode,
        String accountGroup,
        String depositTypeCode,
        String accountNoMasked,
        String accountName,
        BigDecimal balance,
        String currencyCode,
        LocalDate accountStartDate,
        LocalDate lastTranDate,
        Boolean overdraftYn,
        LocalDate nextPaymentDate
) {
}
