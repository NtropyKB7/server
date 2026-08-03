package com.ntropy.common.client;

import com.ntropy.common.dto.work.summary.PlannedWorkIncomeSummary;

import java.time.LocalDate;
import java.util.List;

public interface PlannedWorkIncomeQueryClient {
    List<PlannedWorkIncomeSummary> findPlannedWorkIncome(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate);
}
