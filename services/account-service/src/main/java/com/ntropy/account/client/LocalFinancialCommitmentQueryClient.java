package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.service.FinancialCommitmentService;
import com.ntropy.common.client.FinancialCommitmentQueryClient;
import com.ntropy.common.dto.account.FinancialCommitmentSummary;

import lombok.RequiredArgsConstructor;

/** account-service의 금융 납입 예정 추정 Service를 defense-service에 연결하는 Local Client. */
@Component
@RequiredArgsConstructor
public class LocalFinancialCommitmentQueryClient implements FinancialCommitmentQueryClient {

    private final FinancialCommitmentService financialCommitmentService;

    @Override
    public List<FinancialCommitmentSummary> findFinancialCommitments(Long userId, LocalDate fromDate, LocalDate toDate) {
        return financialCommitmentService.findFinancialCommitments(userId, fromDate, toDate);
    }
}
