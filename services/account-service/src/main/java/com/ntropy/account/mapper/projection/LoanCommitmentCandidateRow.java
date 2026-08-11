package com.ntropy.account.mapper.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 대출 계좌 1건과 가장 최근 정상 상환 LOAN 거래(있으면)를 함께 담은 조회 행. */
@Getter
@Setter
public class LoanCommitmentCandidateRow {
    private Long accountId;
    private String productName;
    private BigDecimal outstandingBalance;
    private LocalDate nextPaymentDate;
    private BigDecimal expectedAmount;
}
