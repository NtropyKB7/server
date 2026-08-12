package com.ntropy.work.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

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
 *
 * <p>settlement_offset_unit이 BUSINESS_DAY인 경우(현재 배민커넥트/쿠팡이츠 배달파트너)
 * offset일만큼 역산할 때 주말·공휴일을 건너뛴다. 주말 판정은 외부 데이터 없이 계산하고,
 * 공휴일은 holidays 파라미터로 주입받는다 - 이 클래스는 순수 함수로 유지하고, 실제 공휴일
 * 조회(홀리데이 API/캐시)는 호출부(SettlementService)의 책임으로 둔다.</p>
 */
public final class SettlementPeriodCalculator {

    private static final int DEFAULT_WEEKLY_OFFSET_DAY = 1;
    private static final String BUSINESS_DAY = "BUSINESS_DAY";

    private SettlementPeriodCalculator() {
    }

    public static SettlementPeriod calculate(Platform platform, LocalDate paymentDate, Set<LocalDate> holidays) {
        return switch (platform.getSettlementCycle()) {
            case "DAILY" -> dailyPeriod(platform, paymentDate, holidays);
            case "WEEKLY" -> weeklyPeriod(platform, paymentDate, holidays);
            case "MONTHLY" -> monthlyPeriod(paymentDate);
            default -> throw new IllegalArgumentException(
                    "알 수 없는 정산 주기입니다: " + platform.getSettlementCycle());
        };
    }

    private static SettlementPeriod dailyPeriod(Platform platform, LocalDate paymentDate, Set<LocalDate> holidays) {
        int offset = platform.getSettlementOffsetDay() == null ? 0 : platform.getSettlementOffsetDay();
        LocalDate workDate = subtractDays(paymentDate, offset, platform.getSettlementOffsetUnit(), holidays);
        return new SettlementPeriod(workDate, workDate);
    }

    private static SettlementPeriod weeklyPeriod(Platform platform, LocalDate paymentDate, Set<LocalDate> holidays) {
        int offset = platform.getSettlementOffsetDay() == null
                ? DEFAULT_WEEKLY_OFFSET_DAY
                : platform.getSettlementOffsetDay();
        LocalDate periodEnd = subtractDays(paymentDate, offset, platform.getSettlementOffsetUnit(), holidays);
        LocalDate periodStart = periodEnd.minusDays(6);
        return new SettlementPeriod(periodStart, periodEnd);
    }

    private static SettlementPeriod monthlyPeriod(LocalDate paymentDate) {
        YearMonth previousMonth = YearMonth.from(paymentDate).minusMonths(1);
        return new SettlementPeriod(previousMonth.atDay(1), previousMonth.atEndOfMonth());
    }

    /**
     * unit이 BUSINESS_DAY면 주말·공휴일을 건너뛰며 days만큼 역산하고, 그 외(CALENDAR_DAY 또는
     * 아직 값이 없는 경우)는 기존처럼 달력일 그대로 뺀다.
     */
    private static LocalDate subtractDays(LocalDate date, int days, String unit, Set<LocalDate> holidays) {
        if (!BUSINESS_DAY.equals(unit)) {
            return date.minusDays(days);
        }
        LocalDate result = date;
        int remaining = days;
        while (remaining > 0) {
            result = result.minusDays(1);
            if (!isWeekend(result) && !holidays.contains(result)) {
                remaining--;
            }
        }
        return result;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}
