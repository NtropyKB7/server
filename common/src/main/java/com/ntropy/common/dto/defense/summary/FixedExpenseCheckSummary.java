package com.ntropy.common.dto.defense.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FixedExpenseCheckSummary {
    private Long totalExpectedAmount;
    private List<FixedExpenseSummary> expenses;
}
