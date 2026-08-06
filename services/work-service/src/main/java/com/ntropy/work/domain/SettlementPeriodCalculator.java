package com.ntropy.work.domain;

import java.time.LocalDate;
import java.time.YearMonth;

import com.ntropy.work.domain.entity.Platform;

/**
 * PLATFORM.settlement_cycle 기준으로, 입금(정산)일로부터 실제 근무가 이뤄진 기간을 역산한다.
 * settlement_offset_day는 "정산 기간 종료일과 입금일 사이의 간격(일)"으로 DAILY/WEEKLY 공통으로
 * 쓴다 (쿠팡이츠 배달파트너 실제 사례: 매주 금요일 입금, 정산 기간은 전주 수요일~이번주 화요일
 * → 종료일(화)이 입금일(금)보다 3일 전이므로 offset=3).
 * WEEKLY에서 offset이 없으면 1(입금일 하루 전)을 기본값으로 쓰는데, 이는 검증되지 않은
 * 추정값이라 실제 플랫폼별 값이 확인되면 seed 데이터를 갱신해야 한다.
 * MONTHLY 기준일의 정확한 정산 규칙도 마찬가지로 검증되지 않은 추정값이다:
 * - MONTHLY: 입금일이 속한 달의 직전 달 전체
 */
public final class SettlementPeriodCalculator {

    private static final int DEFAULT_WEEKLY_OFFSET_DAY = 1;

    private SettlementPeriodCalculator() {
    }

    public static SettlementPeriod calculate(Platform platform, LocalDate paymentDate) {
        return switch (platform.getSettlementCycle()) {
            case "DAILY" -> dailyPeriod(platform, paymentDate);
            case "WEEKLY" -> weeklyPeriod(platform, paymentDate);
            case "MONTHLY" -> monthlyPeriod(paymentDate);
            default -> throw new IllegalArgumentException(
                    "알 수 없는 정산 주기입니다: " + platform.getSettlementCycle());
        };
    }

    private static SettlementPeriod dailyPeriod(Platform platform, LocalDate paymentDate) {
        int offset = platform.getSettlementOffsetDay() == null ? 0 : platform.getSettlementOffsetDay();
        LocalDate workDate = paymentDate.minusDays(offset);
        return new SettlementPeriod(workDate, workDate);
    }

    private static SettlementPeriod weeklyPeriod(Platform platform, LocalDate paymentDate) {
        int offset = platform.getSettlementOffsetDay() == null
                ? DEFAULT_WEEKLY_OFFSET_DAY
                : platform.getSettlementOffsetDay();
        LocalDate periodEnd = paymentDate.minusDays(offset);
        LocalDate periodStart = periodEnd.minusDays(6);
        return new SettlementPeriod(periodStart, periodEnd);
    }

    private static SettlementPeriod monthlyPeriod(LocalDate paymentDate) {
        YearMonth previousMonth = YearMonth.from(paymentDate).minusMonths(1);
        return new SettlementPeriod(previousMonth.atDay(1), previousMonth.atEndOfMonth());
    }
}
