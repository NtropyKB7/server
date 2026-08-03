package com.ntropy.common.dto.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCommitmentSummary {
    private Long commitmentId;
    private Long accountId;
    private String expenseType;
    private String productName;
    private Long outstandingBalance;
    private Long expectedAmount;
    private LocalDate nextPaymentDate;
    private String amountStatus;
    private String dateStatus;
}
