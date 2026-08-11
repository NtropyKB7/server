package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Platform;

class SettlementPeriodCalculatorTest {

    @Test
    @DisplayName("DAILY 정산은 offset일 이전 하루를 근무 기간으로 본다")
    void calculate_daily_subtractsOffsetDays() {
        Platform platform = Platform.builder().settlementCycle("DAILY").settlementOffsetDay(1).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 9), period.start());
        assertEquals(LocalDate.of(2026, 8, 9), period.end());
    }

    @Test
    @DisplayName("DAILY 정산에서 offset이 없으면 입금일 당일을 근무일로 본다")
    void calculate_daily_nullOffsetDefaultsToZero() {
        Platform platform = Platform.builder().settlementCycle("DAILY").settlementOffsetDay(null).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 10), period.start());
        assertEquals(LocalDate.of(2026, 8, 10), period.end());
    }

    @Test
    @DisplayName("WEEKLY 정산에서 offset이 없으면 입금일 하루 전까지 7일을 근무 기간으로 본다 (미검증 기본값)")
    void calculate_weekly_nullOffsetDefaultsToOneDay() {
        Platform platform = Platform.builder().settlementCycle("WEEKLY").settlementOffsetDay(null).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14), Set.of());

        assertEquals(LocalDate.of(2026, 8, 7), period.start());
        assertEquals(LocalDate.of(2026, 8, 13), period.end());
    }

    @Test
    @DisplayName("WEEKLY 정산은 offset일 이전까지 7일을 근무 기간으로 본다 (쿠팡이츠 배달파트너 실제 사례: 금요일 입금, offset=3 → 전주 수~이번주 화)")
    void calculate_weekly_withOffset_matchesCoupangEatsPattern() {
        Platform platform = Platform.builder().settlementCycle("WEEKLY").settlementOffsetDay(3).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.start());
        assertEquals(LocalDate.of(2026, 8, 11), period.end());
    }

    @Test
    @DisplayName("MONTHLY 정산은 입금일이 속한 달의 직전 달 전체를 근무 기간으로 본다")
    void calculate_monthly_returnsPreviousCalendarMonth() {
        Platform platform = Platform.builder().settlementCycle("MONTHLY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 21), Set.of());

        assertEquals(LocalDate.of(2026, 7, 1), period.start());
        assertEquals(LocalDate.of(2026, 7, 31), period.end());
    }

    @Test
    @DisplayName("알 수 없는 정산 주기는 예외를 던진다")
    void calculate_unknownCycle_throws() {
        Platform platform = Platform.builder().settlementCycle("YEARLY").build();

        assertThrows(IllegalArgumentException.class,
                () -> SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 21), Set.of()));
    }

    @Test
    @DisplayName("CALENDAR_DAY(기본값)는 주말이 껴 있어도 그대로 달력일만큼 뺀다")
    void calculate_calendarDayUnit_ignoresWeekend() {
        // 2026-08-10(월)에서 3일 전 = 2026-08-07(금) - 주말(08/08,09) 안 건너뜀
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(3).settlementOffsetUnit("CALENDAR_DAY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 7), period.start());
    }

    @Test
    @DisplayName("BUSINESS_DAY는 역산 도중 낀 주말을 건너뛴다 (배민커넥트 사례: 월요일 입금, 3영업일 전)")
    void calculate_businessDayUnit_skipsWeekend() {
        // 2026-08-10(월)에서 영업일 3일 전: 금(08/07)-1, 목(08/06)-2, 수(08/05)-3
        // 중간에 낀 주말(08/08 토, 08/09 일)은 카운트하지 않음
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.start());
    }

    @Test
    @DisplayName("BUSINESS_DAY는 holidays로 주입된 공휴일도 건너뛴다")
    void calculate_businessDayUnit_skipsInjectedHoliday() {
        // 2026-08-17(월)에서 영업일 1일 전인 08/14(금)을 공휴일로 주입하면 08/13(목)이 됨
        Platform platform = Platform.builder()
                .settlementCycle("DAILY").settlementOffsetDay(1).settlementOffsetUnit("BUSINESS_DAY").build();
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 8, 14));

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 17), holidays);

        assertEquals(LocalDate.of(2026, 8, 13), period.start());
    }

    @Test
    @DisplayName("WEEKLY + BUSINESS_DAY도 offset 역산 시 주말을 건너뛴다 (쿠팡이츠 배달파트너 실제 offset=3)")
    void calculate_weeklyBusinessDay_skipsWeekendForPeriodEnd() {
        Platform platform = Platform.builder()
                .settlementCycle("WEEKLY").settlementOffsetDay(3).settlementOffsetUnit("BUSINESS_DAY").build();

        // 2026-08-10(월)에서 영업일 3일 전 = 2026-08-05(수)
        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10), Set.of());

        assertEquals(LocalDate.of(2026, 8, 5), period.end());
        assertEquals(LocalDate.of(2026, 7, 30), period.start());
    }
}
