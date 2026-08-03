package com.ntropy.bff.dto.defense.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ntropy.common.dto.defense.summary.FixedExpenseSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class FixedExpenseResponse {
    private Long commitmentId;
    private Long accountId;
    private String expenseType;
    private String expenseName;
    private String productName;
    private Long outstandingBalance;
    private Long expectedAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate nextPaymentDate;
    private String amountStatus;
    private String dateStatus;
    @JsonProperty("dDayBefore")
    private Integer dDayBefore;
    @JsonProperty("dDayAfter")
    private Integer dDayAfter;
    @JsonProperty("dDayReduction")
    private Integer dDayReduction;
    private String maintainStatus;

    public static FixedExpenseResponse from(FixedExpenseSummary summary) {
        return new FixedExpenseResponse(
                summary.getCommitmentId(), summary.getAccountId(), summary.getExpenseType(),
                summary.getExpenseName(), summary.getProductName(), summary.getOutstandingBalance(),
                summary.getExpectedAmount(), summary.getNextPaymentDate(), summary.getAmountStatus(),
                summary.getDateStatus(), summary.getDDayBefore(), summary.getDDayAfter(),
                summary.getDDayReduction(), summary.getMaintainStatus());
    }
}
