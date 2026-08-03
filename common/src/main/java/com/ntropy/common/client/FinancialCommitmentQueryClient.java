package com.ntropy.common.client;

import com.ntropy.common.dto.account.FinancialCommitmentSummary;

import java.time.LocalDate;
import java.util.List;

public interface FinancialCommitmentQueryClient {
    List<FinancialCommitmentSummary> findFinancialCommitments(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate);
}
