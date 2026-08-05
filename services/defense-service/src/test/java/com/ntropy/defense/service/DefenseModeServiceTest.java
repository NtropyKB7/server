package com.ntropy.defense.service;

import com.ntropy.common.dto.defense.command.DefenseModeEnterCommand;
import com.ntropy.common.dto.defense.command.DefenseModeReleaseCommand;
import com.ntropy.common.dto.diagnosis.DiagnosisDefenseSnapshot;
import com.ntropy.common.dto.account.FinancialCommitmentSummary;
import com.ntropy.common.dto.defense.summary.FixedExpenseCheckSummary;
import com.ntropy.common.dto.defense.summary.FixedExpenseMaintainStatus;
import com.ntropy.common.dto.defense.summary.ExpectedIncomeLossSummary;
import com.ntropy.common.dto.work.summary.JobExpectedIncomeLossSummary;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseCalculationStatus;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.mapper.DefenseModeMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefenseModeServiceTest {
    private final MemoryMapper mapper = new MemoryMapper();
    private final DefenseModeService service = new DefenseModeService(
            mapper,
            userId -> new DiagnosisDefenseSnapshot(4_680_000L, 3_300_000L),
            (userId, fromDate, toDate) -> Collections.emptyList(),
            (userId, fromDate, toDate) -> Collections.emptyList());

    @Test
    void entersAndReleasesDefenseMode() {
        DefenseMode entered = service.enter(new DefenseModeEnterCommand(
                1L, "ACCIDENT_INJURY", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        assertEquals(DefenseModeStatus.ACTIVE, entered.getStatus());
        assertEquals(4_680_000L, entered.getAvailableAssetsSnapshot());
        assertEquals(110_000L, entered.getDailyExpense());
        assertEquals(42, entered.getDDay());
        assertEquals(DefenseCalculationStatus.CALCULATED, entered.getCalculationStatus());

        DefenseMode released = service.release(entered.getDefenseId(),
                new DefenseModeReleaseCommand(1L, LocalDate.of(2026, 8, 8)));
        assertEquals(DefenseModeStatus.RELEASED, released.getStatus());
        assertEquals(LocalDate.of(2026, 8, 8), released.getReturnDate());
    }

    @Test
    void rejectsSecondActiveDefenseMode() {
        DefenseModeEnterCommand command = new DefenseModeEnterCommand(
                1L, "ILLNESS", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));
        service.enter(command);
        assertThrows(ServiceException.class, () -> service.enter(command));
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(ServiceException.class, () -> service.enter(new DefenseModeEnterCommand(
                1L, "OTHER", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 3))));
    }

    @Test
    void allowsEntryWithoutDiagnosisAndMarksCalculationUnavailable() {
        DefenseModeService serviceWithoutDiagnosis = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DiagnosisDefenseSnapshot(null, null),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Collections.emptyList());

        DefenseMode entered = serviceWithoutDiagnosis.enter(new DefenseModeEnterCommand(
                2L, "OTHER", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        assertEquals(DefenseCalculationStatus.DIAGNOSIS_REQUIRED, entered.getCalculationStatus());
        assertEquals(null, entered.getDDay());
    }

    @Test
    void calculatesFixedExpenseImpactAndKeepsUnknownLoanAmountUncalculated() {
        DefenseModeService fixedExpenseService = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DiagnosisDefenseSnapshot(4_680_000L, 3_300_000L),
                (userId, fromDate, toDate) -> Arrays.asList(
                        new FinancialCommitmentSummary(
                                1L, 10L, "SAVING_PAYMENT", "청년희망적금", null,
                                500_000L, LocalDate.of(2026, 8, 5), "CONFIRMED", "ESTIMATED"),
                        new FinancialCommitmentSummary(
                                2L, 20L, "LOAN_REPAYMENT", "신한 직장인 대출", 12_500_000L,
                                null, null, "INSUFFICIENT", "INSUFFICIENT"),
                        new FinancialCommitmentSummary(
                                3L, 30L, "INSURANCE_PREMIUM", "실비 보험", null,
                                100_000L, LocalDate.of(2026, 8, 7), "CONFIRMED", "CONFIRMED")),
                (userId, fromDate, toDate) -> Collections.emptyList());
        DefenseMode entered = fixedExpenseService.enter(new DefenseModeEnterCommand(
                1L, "ACCIDENT_INJURY", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10)));

        FixedExpenseCheckSummary result = fixedExpenseService.getFixedExpenseCheck(entered);

        assertEquals(600_000L, result.getTotalExpectedAmount());
        assertEquals(3, result.getExpenses().size());
        assertEquals(38, result.getExpenses().get(0).getDDayAfter());
        assertEquals(4, result.getExpenses().get(0).getDDayReduction());
        assertEquals(FixedExpenseMaintainStatus.REVIEW_SUSPENSION,
                result.getExpenses().get(0).getMaintainStatus());
        assertEquals(null, result.getExpenses().get(1).getDDayAfter());
        assertEquals(12_500_000L, result.getExpenses().get(1).getOutstandingBalance());
        assertEquals(FixedExpenseMaintainStatus.NORMAL,
                result.getExpenses().get(1).getMaintainStatus());
        assertEquals(FixedExpenseMaintainStatus.DIFFICULT,
                result.getExpenses().get(2).getMaintainStatus());
    }

    @Test
    void calculatesExpectedIncomeLossForCurrentMonthDefensePeriod() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1);
        LocalDate endDate = today.withDayOfMonth(today.lengthOfMonth());
        DefenseModeService incomeLossService = new DefenseModeService(
                new MemoryMapper(),
                userId -> new DiagnosisDefenseSnapshot(4_680_000L, 3_300_000L),
                (userId, fromDate, toDate) -> Collections.emptyList(),
                (userId, fromDate, toDate) -> Arrays.asList(
                        new JobExpectedIncomeLossSummary(101L, "대리운전", 150_000L),
                        new JobExpectedIncomeLossSummary(102L, "배달라이더", 180_000L)));
        DefenseMode entered = incomeLossService.enter(new DefenseModeEnterCommand(
                1L, "ILLNESS", startDate, endDate));

        ExpectedIncomeLossSummary result = incomeLossService.getExpectedIncomeLoss(entered);

        assertEquals(330_000L, result.getTotalAmount());
        assertEquals(startDate, result.getPeriodStartDate());
        assertEquals(endDate, result.getPeriodEndDate());
        assertEquals("CALCULATED", result.getCalculationStatus());
        assertEquals(2, result.getJobs().size());
        assertEquals(150_000L, result.getJobs().get(0).getExpectedIncomeLoss());
    }

    private static class MemoryMapper implements DefenseModeMapper {
        private final Map<Long, DefenseMode> data = new HashMap<>();
        private long sequence;

        @Override
        public DefenseMode findById(Long defenseId) {
            return data.get(defenseId);
        }

        @Override
        public DefenseMode findActiveByUserId(Long userId) {
            return data.values().stream()
                    .filter(mode -> userId.equals(mode.getUserId()) && mode.getStatus() == DefenseModeStatus.ACTIVE)
                    .findFirst().orElse(null);
        }

        @Override
        public int insert(DefenseMode defenseMode) {
            defenseMode.setDefenseId(++sequence);
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }

        @Override
        public int release(DefenseMode defenseMode) {
            data.put(defenseMode.getDefenseId(), defenseMode);
            return 1;
        }
    }
}
