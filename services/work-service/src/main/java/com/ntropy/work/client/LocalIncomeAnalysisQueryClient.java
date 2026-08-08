package com.ntropy.work.client;

import java.time.YearMonth;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.IncomeAnalysisQueryClient;
import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.work.service.IncomeAnalysisService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalIncomeAnalysisQueryClient implements IncomeAnalysisQueryClient {

    private final IncomeAnalysisService incomeAnalysisService;

    @Override
    public MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
        return incomeAnalysisService.getMonthlyIncomeAnalysis(userId, yearMonth);
    }
}
