package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.common.dto.work.summary.EarnedDepositComparison;
import com.ntropy.common.dto.work.summary.JobFatigueSummary;
import com.ntropy.common.dto.work.summary.JobIncomeSummary;
import com.ntropy.common.dto.work.summary.MonthlyIncomeAnalysisSummary;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.mapper.WorkLogPlatformIncomeMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

/**
 * 회원·연월별 소득분석(재무진단 연계 §1~14 기준)을 계산한다.
 *
 * <p>SettlementService가 배치로 미리 만들어 둔 SETTLEMENT 행(MATCHED/UNMATCHED, deposit_date
 * 기준)을 그대로 집계한다. 한 플랫폼에 회원 잡이 여러 개 매핑되는 경우(AMBIGUOUS)는 없다고
 * 가정하고 다루지 않으며, ambiguousTransactionCount는 항상 0으로 내려간다.</p>
 *
 * <p>변동성(§9)만 예외적으로 특정 과거 월 조회가 실패해도 그 달을 제외하고 계산한다
 * (2개월 미만이면 null). 그 외 totalIncome/previousMonthIncome 등 핵심 지표는 실패를
 * 감추지 않고 예외를 그대로 전파한다(§12) — diagnosis-service는 이 경우 기존 정상
 * DIAGNOSIS_RESULT를 유지한다.</p>
 */
@Service
@RequiredArgsConstructor
public class IncomeAnalysisService {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final int VOLATILITY_MIN_VALID_MONTHS = 2;

    private final SettlementMapper settlementMapper;
    private final JobMapper jobMapper;
    private final WorkLogMapper workLogMapper;
    private final WorkLogPlatformIncomeMapper workLogPlatformIncomeMapper;

    public MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(Long userId, YearMonth yearMonth) {
        Map<Long, String> jobNames = jobMapper.findByUserId(userId).stream()
                .collect(Collectors.toMap(Job::getJobId, Job::getJobName, (a, b) -> a));

        // 이번 달 본체 조회/계산 실패는 그대로 전파한다 (§12) - diagnosis-service가 기존 스냅샷을 유지하도록.
        MonthAggregate current = aggregateMonth(userId, yearMonth);

        // 전월 조회 실패는 이번 달 분석 전체를 실패시키지 않고 null로 구분한다 (§8).
        // previousMonthIncome=null: 조회 실패, previousMonthIncome=0L: 실제 전월 소득 0원.
        Long previousMonthIncome = fetchTotalIncomeSafely(userId, yearMonth.minusMonths(1));
        Long changeAmount = previousMonthIncome == null ? null : current.totalIncome() - previousMonthIncome;
        Double changeRate = (previousMonthIncome == null || previousMonthIncome == 0)
                ? null
                : (double) changeAmount / previousMonthIncome;

        return MonthlyIncomeAnalysisSummary.builder()
                .userId(userId)
                .yearMonth(yearMonth)
                .asOfDate(resolveAsOfDate(yearMonth))
                .totalIncome(current.totalIncome())
                .unmatchedIncome(current.unmatchedIncome())
                .pendingSettlementIncome(calculatePendingSettlementIncome(userId, yearMonth))
                .matchedTransactionCount(current.matchedCount())
                .unmatchedTransactionCount(current.unmatchedCount())
                .ambiguousTransactionCount(0)
                .jobIncomes(buildJobIncomes(current, jobNames))
                .primaryJobId(resolvePrimaryJobId(current))
                .primaryJobName(resolvePrimaryJobName(current, jobNames))
                .previousMonthIncome(previousMonthIncome)
                .incomeChangeAmount(changeAmount)
                .incomeChangeRate(changeRate)
                .incomeVolatility(calculateVolatility(current.totalIncome(), previousMonthIncome, userId, yearMonth))
                .earnedDepositComparisons(buildEarnedDepositComparisons(userId, yearMonth, current, jobNames))
                .fatigueSummaries(buildFatigueSummaries(userId, yearMonth, jobNames))
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    /** 조회/계산 실패 시 null을 반환해 "실패"와 "실제 0원"을 구분한다. */
    private Long fetchTotalIncomeSafely(Long userId, YearMonth yearMonth) {
        try {
            return aggregateMonth(userId, yearMonth).totalIncome();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private LocalDate resolveAsOfDate(YearMonth yearMonth) {
        return yearMonth.equals(YearMonth.now()) ? LocalDate.now() : yearMonth.atEndOfMonth();
    }

    // ---------- SETTLEMENT 집계 ----------

    private MonthAggregate aggregateMonth(Long userId, YearMonth yearMonth) {
        List<Settlement> settlements = settlementMapper.findByUserIdAndDepositDateRange(
                userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        MonthAggregate aggregate = new MonthAggregate();
        for (Settlement settlement : settlements) {
            aggregate.apply(settlement);
        }
        return aggregate;
    }

    /**
     * 확정(CONFIRMED)됐지만 아직 COMPLETED가 아닌 WORK_LOG_PLATFORM_INCOME 행들의 합계.
     * 근무일지 전체가 아니라 income 행 단위로 보는 이유: 여러 플랫폼을 동시에 뛴 근무일지는
     * 일부 플랫폼만 정산 완료(PARTIAL)될 수 있어서, 아직 안 들어온 플랫폼 몫만 집계해야
     * 정확하다. 매핑된 플랫폼이 없는 잡의 근무일지(income 행 자체가 없음)는 이 집계에서
     * 빠진다 - 알려진 한계.
     */
    private Long calculatePendingSettlementIncome(Long userId, YearMonth yearMonth) {
        return workLogPlatformIncomeMapper.findConfirmedByUserIdAndDateRange(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .stream()
                .filter(income -> income.getSettlementStatus() != SettlementStatus.COMPLETED)
                .mapToLong(income -> income.getExpectedAmount() == null ? 0L : income.getExpectedAmount())
                .sum();
    }

    // ---------- 잡별 소득 (§7) ----------

    private List<JobIncomeSummary> buildJobIncomes(MonthAggregate current, Map<Long, String> jobNames) {
        List<JobIncomeSummary> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : current.incomeByJob().entrySet()) {
            Long jobId = entry.getKey();
            long amount = entry.getValue();
            double ratio = current.totalIncome() == 0 ? 0 : (double) amount / current.totalIncome();
            int count = current.transactionCountByJob().getOrDefault(jobId, 0);
            result.add(new JobIncomeSummary(jobId, jobNames.get(jobId), amount, ratio, count));
        }
        return result;
    }

    /** 소득금액이 가장 큰 잡이 여러 개로 동률이면 임의로 고르지 않고 null을 반환한다(§7). */
    private Long resolvePrimaryJobId(MonthAggregate current) {
        List<Long> topJobs = findTopIncomeJobs(current);
        return topJobs.size() == 1 ? topJobs.get(0) : null;
    }

    private String resolvePrimaryJobName(MonthAggregate current, Map<Long, String> jobNames) {
        Long primaryJobId = resolvePrimaryJobId(current);
        return primaryJobId == null ? null : jobNames.get(primaryJobId);
    }

    private List<Long> findTopIncomeJobs(MonthAggregate current) {
        Map<Long, Long> incomeByJob = current.incomeByJob();
        if (incomeByJob.isEmpty()) {
            return List.of();
        }
        long maxAmount = incomeByJob.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        return incomeByJob.entrySet().stream()
                .filter(entry -> entry.getValue() == maxAmount)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ---------- 소득 변동성 (§9) ----------

    /**
     * 대상 월을 포함한 최근 최대 3개월의 변동계수. 이번 달/전월 값은 이미 조회된 값을 재사용하고,
     * 2개월 전만 추가로 안전 조회한다. 조회 실패한 달은 제외하며, 유효한 월이 2개 미만이거나
     * 평균소득이 0이면 null.
     */
    private Double calculateVolatility(long currentMonthIncome, Long previousMonthIncome,
                                        Long userId, YearMonth targetMonth) {
        List<Long> monthlyIncomes = new ArrayList<>();
        monthlyIncomes.add(currentMonthIncome);
        if (previousMonthIncome != null) {
            monthlyIncomes.add(previousMonthIncome);
        }
        Long twoMonthsAgoIncome = fetchTotalIncomeSafely(userId, targetMonth.minusMonths(2));
        if (twoMonthsAgoIncome != null) {
            monthlyIncomes.add(twoMonthsAgoIncome);
        }

        if (monthlyIncomes.size() < VOLATILITY_MIN_VALID_MONTHS) {
            return null;
        }
        double mean = monthlyIncomes.stream().mapToLong(Long::longValue).average().orElse(0);
        if (mean == 0) {
            return null;
        }
        double variance = monthlyIncomes.stream()
                .mapToDouble(income -> Math.pow(income - mean, 2))
                .sum() / monthlyIncomes.size();
        return Math.sqrt(variance) / mean;
    }

    // ---------- 발생소득 vs 실입금소득 (§10) ----------

    private List<EarnedDepositComparison> buildEarnedDepositComparisons(
            Long userId, YearMonth yearMonth, MonthAggregate current, Map<Long, String> jobNames) {
        Map<Long, Long> earnedByJob = new LinkedHashMap<>();
        for (WorkLog workLog : findWorkLogsInMonth(userId, yearMonth)) {
            if (!STATUS_CONFIRMED.equals(workLog.getStatus())) {
                continue;
            }
            long income = workLog.getEstimatedIncome() == null ? 0 : workLog.getEstimatedIncome();
            earnedByJob.merge(workLog.getJobId(), income, Long::sum);
        }

        Set<Long> jobIds = new LinkedHashSet<>();
        jobIds.addAll(earnedByJob.keySet());
        jobIds.addAll(current.incomeByJob().keySet());

        List<EarnedDepositComparison> result = new ArrayList<>();
        for (Long jobId : jobIds) {
            long earned = earnedByJob.getOrDefault(jobId, 0L);
            long deposited = current.incomeByJob().getOrDefault(jobId, 0L);
            result.add(new EarnedDepositComparison(jobId, jobNames.get(jobId), earned, deposited, deposited - earned));
        }
        return result;
    }

    // ---------- 잡별 피로도 (§11) ----------

    /**
     * WorkLog.fatigue는 등록 시점(WorkLogService)에 이미 계획=기본피로도/확정=실제피로도로
     * 채워져 있으므로, 완료/미래 일정을 여기서 다시 구분하지 않고 그대로 가중평균한다.
     */
    private List<JobFatigueSummary> buildFatigueSummaries(Long userId, YearMonth yearMonth, Map<Long, String> jobNames) {
        Map<Long, List<WorkLog>> byJob = findWorkLogsInMonth(userId, yearMonth).stream()
                .collect(Collectors.groupingBy(WorkLog::getJobId, LinkedHashMap::new, Collectors.toList()));

        List<JobFatigueSummary> result = new ArrayList<>();
        for (Map.Entry<Long, List<WorkLog>> entry : byJob.entrySet()) {
            Long jobId = entry.getKey();
            List<WorkLog> jobLogs = entry.getValue();

            int workDays = (int) jobLogs.stream().map(WorkLog::getWorkDate).distinct().count();
            long totalMinutes = jobLogs.stream()
                    .mapToLong(log -> WorkTimeUtils.durationMinutes(log.getStartTime(), log.getEndTime()))
                    .sum();
            Double averageFatigue = totalMinutes == 0 ? null : jobLogs.stream()
                    .mapToDouble(log -> log.getFatigue() * WorkTimeUtils.durationMinutes(log.getStartTime(), log.getEndTime()))
                    .sum() / totalMinutes;
            Long latestFatigue = jobLogs.stream()
                    .max(Comparator.comparing(WorkLog::getWorkDate))
                    .map(WorkLog::getFatigue)
                    .orElse(null);

            result.add(new JobFatigueSummary(jobId, jobNames.get(jobId), workDays, totalMinutes, averageFatigue, latestFatigue));
        }
        return result;
    }

    private List<WorkLog> findWorkLogsInMonth(Long userId, YearMonth yearMonth) {
        return workLogMapper.findByUserIdAndDateRange(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    // ---------- 월 집계 값객체 ----------

    private static final class MonthAggregate {
        private long totalIncome = 0;
        private long unmatchedIncome = 0;
        private int matchedCount = 0;
        private int unmatchedCount = 0;
        private final Map<Long, Long> incomeByJob = new LinkedHashMap<>();
        private final Map<Long, Integer> transactionCountByJob = new LinkedHashMap<>();

        private void apply(Settlement settlement) {
            long amount = settlement.getActualAmount();
            int count = settlement.getTransactionCount() == null ? 1 : settlement.getTransactionCount();
            if (settlement.getStatus() == SettlementMatchStatus.MATCHED) {
                totalIncome += amount;
                matchedCount += count;
                incomeByJob.merge(settlement.getJobId(), amount, Long::sum);
                transactionCountByJob.merge(settlement.getJobId(), count, Integer::sum);
            } else {
                unmatchedIncome += amount;
                unmatchedCount += count;
            }
        }

        private long totalIncome() {
            return totalIncome;
        }

        private long unmatchedIncome() {
            return unmatchedIncome;
        }

        private int matchedCount() {
            return matchedCount;
        }

        private int unmatchedCount() {
            return unmatchedCount;
        }

        private Map<Long, Long> incomeByJob() {
            return incomeByJob;
        }

        private Map<Long, Integer> transactionCountByJob() {
            return transactionCountByJob;
        }
    }
}
