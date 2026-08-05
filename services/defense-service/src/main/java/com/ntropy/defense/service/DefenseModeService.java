package com.ntropy.defense.service;

import com.ntropy.common.client.DiagnosisQueryClient;
import com.ntropy.common.client.FinancialCommitmentQueryClient;
import com.ntropy.common.client.ExpectedIncomeLossQueryClient;
import com.ntropy.common.dto.account.FinancialCommitmentSummary;
import com.ntropy.common.dto.defense.command.DefenseModeEnterCommand;
import com.ntropy.common.dto.defense.command.DefenseModeReleaseCommand;
import com.ntropy.common.dto.defense.summary.FixedExpenseCheckSummary;
import com.ntropy.common.dto.defense.summary.FixedExpenseSummary;
import com.ntropy.common.dto.defense.summary.FixedExpenseMaintainStatus;
import com.ntropy.common.dto.defense.summary.ExpectedIncomeLossSummary;
import com.ntropy.common.dto.work.summary.JobExpectedIncomeLossSummary;
import com.ntropy.common.dto.diagnosis.DiagnosisDefenseSnapshot;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.defense.domain.DefenseCause;
import com.ntropy.defense.domain.DefenseCalculationStatus;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseModeStatus;
import com.ntropy.defense.exception.DefenseErrorCode;
import com.ntropy.defense.mapper.DefenseModeMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefenseModeService {
    private final DefenseModeMapper defenseModeMapper;
    private final DiagnosisQueryClient diagnosisQueryClient;
    private final FinancialCommitmentQueryClient financialCommitmentQueryClient;
    private final ExpectedIncomeLossQueryClient expectedIncomeLossQueryClient;

    @Autowired
    public DefenseModeService(
            DefenseModeMapper defenseModeMapper,
            ObjectProvider<DiagnosisQueryClient> diagnosisQueryClientProvider,
            ObjectProvider<FinancialCommitmentQueryClient> financialCommitmentQueryClientProvider,
            ObjectProvider<ExpectedIncomeLossQueryClient> expectedIncomeLossQueryClientProvider) {
        this(
                defenseModeMapper,
                diagnosisQueryClientProvider.getIfAvailable(() -> userId -> null),
                financialCommitmentQueryClientProvider.getIfAvailable(
                        () -> (userId, fromDate, toDate) -> Collections.emptyList()),
                expectedIncomeLossQueryClientProvider.getIfAvailable(
                        () -> (userId, fromDate, toDate) -> Collections.emptyList()));
    }

    public DefenseModeService(
            DefenseModeMapper defenseModeMapper,
            DiagnosisQueryClient diagnosisQueryClient,
            FinancialCommitmentQueryClient financialCommitmentQueryClient,
            ExpectedIncomeLossQueryClient expectedIncomeLossQueryClient) {
        this.defenseModeMapper = defenseModeMapper;
        this.diagnosisQueryClient = diagnosisQueryClient;
        this.financialCommitmentQueryClient = financialCommitmentQueryClient;
        this.expectedIncomeLossQueryClient = expectedIncomeLossQueryClient;
    }

    @Transactional
    public DefenseMode enter(DefenseModeEnterCommand command) {
        validateEnterCommand(command);
        if (defenseModeMapper.findActiveByUserId(command.getUserId()) != null) {
            throw new ServiceException(DefenseErrorCode.ALREADY_ACTIVE);
        }

        DefenseMode defenseMode = new DefenseMode();
        defenseMode.setUserId(command.getUserId());
        defenseMode.setCauseCode(parseCause(command.getCauseCode()));
        defenseMode.setUnavailableStartDate(command.getUnavailableStartDate());
        defenseMode.setExpectedReturnDate(command.getExpectedReturnDate());
        applyDiagnosisSnapshot(defenseMode, diagnosisQueryClient.getDefenseSnapshot(command.getUserId()));
        defenseMode.setStatus(DefenseModeStatus.ACTIVE);
        defenseModeMapper.insert(defenseMode);
        return defenseModeMapper.findById(defenseMode.getDefenseId());
    }

    public DefenseMode getCurrent(Long userId) {
        if (userId == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }
        DefenseMode defenseMode = defenseModeMapper.findActiveByUserId(userId);
        if (defenseMode == null) {
            throw new ServiceException(DefenseErrorCode.NOT_FOUND);
        }
        return defenseMode;
    }

    public FixedExpenseCheckSummary getFixedExpenseCheck(DefenseMode defenseMode) {
        List<FinancialCommitmentSummary> commitments = financialCommitmentQueryClient.findFinancialCommitments(
                defenseMode.getUserId(),
                defenseMode.getUnavailableStartDate(),
                defenseMode.getExpectedReturnDate());
        if (commitments == null) {
            commitments = Collections.emptyList();
        }

        List<FixedExpenseSummary> expenses = commitments.stream()
                .map(commitment -> toFixedExpense(defenseMode, commitment))
                .collect(Collectors.toList());
        long totalExpectedAmount = commitments.stream()
                .map(FinancialCommitmentSummary::getExpectedAmount)
                .filter(amount -> amount != null && amount > 0)
                .mapToLong(Long::longValue)
                .sum();
        return new FixedExpenseCheckSummary(totalExpectedAmount, expenses);
    }

    public ExpectedIncomeLossSummary getExpectedIncomeLoss(DefenseMode defenseMode) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate periodStartDate = laterOf(defenseMode.getUnavailableStartDate(), currentMonth.atDay(1));
        LocalDate periodEndDate = earlierOf(defenseMode.getExpectedReturnDate(), currentMonth.atEndOfMonth());
        if (periodStartDate.isAfter(periodEndDate)) {
            return new ExpectedIncomeLossSummary(0L, null, null, "NO_SCHEDULE", Collections.emptyList());
        }

        List<JobExpectedIncomeLossSummary> jobs = expectedIncomeLossQueryClient.findExpectedIncomeLossByJob(
                defenseMode.getUserId(), periodStartDate, periodEndDate);
        if (jobs == null || jobs.isEmpty()) {
            return new ExpectedIncomeLossSummary(
                    0L, periodStartDate, periodEndDate, "NO_SCHEDULE", Collections.emptyList());
        }

        long totalAmount = jobs.stream()
                .map(JobExpectedIncomeLossSummary::getExpectedIncomeLoss)
                .filter(income -> income != null && income > 0)
                .mapToLong(Long::longValue)
                .sum();
        long calculatedJobCount = jobs.stream()
                .filter(job -> job.getExpectedIncomeLoss() != null)
                .count();
        String calculationStatus;
        if (calculatedJobCount == 0) {
            calculationStatus = "INSUFFICIENT";
        } else if (calculatedJobCount < jobs.size()) {
            calculationStatus = "PARTIALLY_CALCULATED";
        } else {
            calculationStatus = "CALCULATED";
        }
        return new ExpectedIncomeLossSummary(
                totalAmount, periodStartDate, periodEndDate, calculationStatus, jobs);
    }

    @Transactional
    public DefenseMode release(Long defenseId, DefenseModeReleaseCommand command) {
        if (defenseId == null || command == null || command.getUserId() == null || command.getReturnDate() == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }

        DefenseMode defenseMode = defenseModeMapper.findById(defenseId);
        if (defenseMode == null) {
            throw new ServiceException(DefenseErrorCode.NOT_FOUND);
        }
        if (!command.getUserId().equals(defenseMode.getUserId())) {
            throw new ServiceException(DefenseErrorCode.ACCESS_DENIED);
        }
        if (defenseMode.getStatus() != DefenseModeStatus.ACTIVE) {
            throw new ServiceException(DefenseErrorCode.NOT_ACTIVE);
        }
        if (command.getReturnDate().isBefore(defenseMode.getUnavailableStartDate())) {
            throw new ServiceException(DefenseErrorCode.INVALID_PERIOD);
        }

        defenseMode.setReturnDate(command.getReturnDate());
        defenseMode.setStatus(DefenseModeStatus.RELEASED);
        defenseModeMapper.release(defenseMode);
        return defenseModeMapper.findById(defenseId);
    }

    private void validateEnterCommand(DefenseModeEnterCommand command) {
        if (command == null || command.getUserId() == null || command.getCauseCode() == null
                || command.getUnavailableStartDate() == null || command.getExpectedReturnDate() == null) {
            throw new ServiceException(DefenseErrorCode.INVALID_REQUEST);
        }
        if (command.getExpectedReturnDate().isBefore(command.getUnavailableStartDate())) {
            throw new ServiceException(DefenseErrorCode.INVALID_PERIOD);
        }
    }

    private DefenseCause parseCause(String causeCode) {
        try {
            return DefenseCause.valueOf(causeCode);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(DefenseErrorCode.INVALID_CAUSE);
        }
    }

    private void applyDiagnosisSnapshot(DefenseMode defenseMode, DiagnosisDefenseSnapshot snapshot) {
        Long liquidAssets = snapshot == null ? null : snapshot.getLiquidAssets();
        Long averageMonthlyExpense = snapshot == null ? null : snapshot.getAverageMonthlyExpense();

        defenseMode.setAvailableAssetsSnapshot(liquidAssets);
        defenseMode.setAverageMonthlyExpense(averageMonthlyExpense);

        if (liquidAssets == null) {
            defenseMode.setCalculationStatus(DefenseCalculationStatus.DIAGNOSIS_REQUIRED);
            return;
        }
        if (averageMonthlyExpense == null || averageMonthlyExpense <= 0) {
            defenseMode.setCalculationStatus(DefenseCalculationStatus.EXPENSE_DATA_REQUIRED);
            return;
        }

        long dailyExpense = (long) Math.ceil(averageMonthlyExpense / 30.0);
        long calculatedDays = liquidAssets / dailyExpense;
        defenseMode.setDailyExpense(dailyExpense);
        defenseMode.setDDay(calculatedDays > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) calculatedDays);
        defenseMode.setCalculationStatus(DefenseCalculationStatus.CALCULATED);
    }

    private FixedExpenseSummary toFixedExpense(
            DefenseMode defenseMode,
            FinancialCommitmentSummary commitment) {
        Integer dDayAfter = null;
        Integer dDayReduction = null;
        Long expectedAmount = commitment.getExpectedAmount();
        if (defenseMode.getDDay() != null
                && defenseMode.getAvailableAssetsSnapshot() != null
                && defenseMode.getDailyExpense() != null
                && defenseMode.getDailyExpense() > 0
                && expectedAmount != null
                && expectedAmount >= 0
                && !"INSUFFICIENT".equals(commitment.getAmountStatus())) {
            long assetsAfterPayment = Math.max(defenseMode.getAvailableAssetsSnapshot() - expectedAmount, 0L);
            long calculatedDays = assetsAfterPayment / defenseMode.getDailyExpense();
            dDayAfter = calculatedDays > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) calculatedDays;
            dDayReduction = Math.max(defenseMode.getDDay() - dDayAfter, 0);
        }

        FixedExpenseMaintainStatus maintainStatus = maintainStatus(commitment.getExpenseType());
        return new FixedExpenseSummary(
                commitment.getCommitmentId(),
                commitment.getAccountId(),
                commitment.getExpenseType(),
                expenseName(commitment.getExpenseType()),
                commitment.getProductName(),
                commitment.getOutstandingBalance(),
                expectedAmount,
                commitment.getNextPaymentDate(),
                commitment.getAmountStatus(),
                commitment.getDateStatus(),
                defenseMode.getDDay(),
                dDayAfter,
                dDayReduction,
                maintainStatus);
    }

    private FixedExpenseMaintainStatus maintainStatus(String expenseType) {
        if ("LOAN_REPAYMENT".equals(expenseType)) {
            return FixedExpenseMaintainStatus.NORMAL;
        }
        if ("INSURANCE_PREMIUM".equals(expenseType)) {
            return FixedExpenseMaintainStatus.DIFFICULT;
        }
        if ("SAVING_PAYMENT".equals(expenseType)) {
            return FixedExpenseMaintainStatus.REVIEW_SUSPENSION;
        }
        return FixedExpenseMaintainStatus.UNDETERMINED;
    }

    private String expenseName(String expenseType) {
        if ("SAVING_PAYMENT".equals(expenseType)) {
            return "적금납입";
        }
        if ("LOAN_REPAYMENT".equals(expenseType)) {
            return "대출상환";
        }
        if ("INSURANCE_PREMIUM".equals(expenseType)) {
            return "보험료";
        }
        return "금융지출";
    }

    private LocalDate laterOf(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate earlierOf(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
