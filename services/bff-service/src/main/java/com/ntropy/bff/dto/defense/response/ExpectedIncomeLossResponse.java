package com.ntropy.bff.dto.defense.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ntropy.common.dto.defense.summary.ExpectedIncomeLossSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ExpectedIncomeLossResponse {
    private Long amount;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate periodStartDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate periodEndDate;
    private String calculationStatus;

    public static ExpectedIncomeLossResponse from(ExpectedIncomeLossSummary summary) {
        if (summary == null) {
            return null;
        }
        return new ExpectedIncomeLossResponse(
                summary.getAmount(), summary.getPeriodStartDate(), summary.getPeriodEndDate(),
                summary.getCalculationStatus());
    }
}
