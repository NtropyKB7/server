package com.ntropy.bff.dto.dashboard.response;

import java.util.List;

import com.ntropy.common.dto.work.summary.EarnedDepositComparison;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 이번 달 발생소득 대비 실입금소득.
 * earned는 확정 근무일지 기준 발생소득, deposited는 매칭된 입금 거래 기준 실입금소득이다.
 */
@Getter
@AllArgsConstructor
public class DashboardSettlementResponse {

    private Long deposited;
    private Long earned;

    public static DashboardSettlementResponse from(List<EarnedDepositComparison> comparisons) {
        if (comparisons == null || comparisons.isEmpty()) {
            return new DashboardSettlementResponse(0L, 0L);
        }
        long deposited = comparisons.stream()
                .mapToLong(comparison -> nullToZero(comparison.getDepositedIncome()))
                .sum();
        long earned = comparisons.stream()
                .mapToLong(comparison -> nullToZero(comparison.getEarnedIncome()))
                .sum();
        return new DashboardSettlementResponse(deposited, earned);
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
