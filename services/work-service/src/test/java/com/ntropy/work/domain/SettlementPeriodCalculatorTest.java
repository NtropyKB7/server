package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Platform;

class SettlementPeriodCalculatorTest {

    @Test
    @DisplayName("DAILY 정산은 offset일 이전 하루를 근무 기간으로 본다")
    void calculate_daily_subtractsOffsetDays() {
        Platform platform = Platform.builder().settlementCycle("DAILY").settlementOffsetDay(1).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10));

        assertEquals(LocalDate.of(2026, 8, 9), period.start());
        assertEquals(LocalDate.of(2026, 8, 9), period.end());
    }

    @Test
    @DisplayName("DAILY 정산에서 offset이 없으면 입금일 당일을 근무일로 본다")
    void calculate_daily_nullOffsetDefaultsToZero() {
        Platform platform = Platform.builder().settlementCycle("DAILY").settlementOffsetDay(null).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 10));

        assertEquals(LocalDate.of(2026, 8, 10), period.start());
        assertEquals(LocalDate.of(2026, 8, 10), period.end());
    }

    @Test
    @DisplayName("WEEKLY 정산에서 offset이 없으면 입금일 하루 전까지 7일을 근무 기간으로 본다 (미검증 기본값)")
    void calculate_weekly_nullOffsetDefaultsToOneDay() {
        Platform platform = Platform.builder().settlementCycle("WEEKLY").settlementOffsetDay(null).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14));

        assertEquals(LocalDate.of(2026, 8, 7), period.start());
        assertEquals(LocalDate.of(2026, 8, 13), period.end());
    }

    @Test
    @DisplayName("WEEKLY 정산은 offset일 이전까지 7일을 근무 기간으로 본다 (쿠팡이츠 배달파트너 실제 사례: 금요일 입금, offset=3 → 전주 수~이번주 화)")
    void calculate_weekly_withOffset_matchesCoupangEatsPattern() {
        Platform platform = Platform.builder().settlementCycle("WEEKLY").settlementOffsetDay(3).build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 14));

        assertEquals(LocalDate.of(2026, 8, 5), period.start());
        assertEquals(LocalDate.of(2026, 8, 11), period.end());
    }

    @Test
    @DisplayName("MONTHLY 정산은 입금일이 속한 달의 직전 달 전체를 근무 기간으로 본다")
    void calculate_monthly_returnsPreviousCalendarMonth() {
        Platform platform = Platform.builder().settlementCycle("MONTHLY").build();

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 21));

        assertEquals(LocalDate.of(2026, 7, 1), period.start());
        assertEquals(LocalDate.of(2026, 7, 31), period.end());
    }

    @Test
    @DisplayName("알 수 없는 정산 주기는 예외를 던진다")
    void calculate_unknownCycle_throws() {
        Platform platform = Platform.builder().settlementCycle("YEARLY").build();

        assertThrows(IllegalArgumentException.class,
                () -> SettlementPeriodCalculator.calculate(platform, LocalDate.of(2026, 8, 21)));
    }
}
