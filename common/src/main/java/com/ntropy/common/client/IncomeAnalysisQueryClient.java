package com.ntropy.common.client;

import java.time.YearMonth;

import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;

/** work-service가 diagnosis-service에 제공하는 회원·연월별 소득분석 조회 계약. */
public interface IncomeAnalysisQueryClient {

    MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(
            Long userId,
            YearMonth yearMonth
    );
}
