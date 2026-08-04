package com.ntropy.common.dto.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
/** account-service가 가공하여 방어모드에 전달하는 금융 납입 예정 항목. */
public class FinancialCommitmentSummary {
    /** 가공 결과를 별도 저장하지 않는 경우 null 허용. */
    private Long commitmentId;

    /** 원본 계좌 또는 금융상품 식별자. */
    private Long accountId;

    /** SAVING_PAYMENT, LOAN_REPAYMENT, INSURANCE_PREMIUM. */
    private String expenseType;
    private String productName;

    /** 대출의 현재 잔액이며 적금·보험은 null 허용. */
    private Long outstandingBalance;

    /** 다음 예상 납입액. 산출할 수 없으면 null. */
    private Long expectedAmount;

    /** 다음 예상 납입일. 산출할 수 없으면 null. */
    private LocalDate nextPaymentDate;

    /** CONFIRMED, ESTIMATED, INSUFFICIENT. */
    private String amountStatus;

    /** CONFIRMED, ESTIMATED, INSUFFICIENT. */
    private String dateStatus;
}
