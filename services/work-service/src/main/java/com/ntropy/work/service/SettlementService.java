package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ntropy.common.client.IncomingTransactionQueryClient;
import com.ntropy.common.dto.account.internal.NormalizedIncomingTransaction;
import com.ntropy.work.domain.PlatformMatchResult;
import com.ntropy.work.domain.PlatformMatcher;
import com.ntropy.work.domain.SettlementPeriod;
import com.ntropy.work.domain.SettlementPeriodCalculator;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.JobMapper;
import com.ntropy.work.mapper.JobPlatformMappingMapper;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.mapper.SettlementMapper;
import com.ntropy.work.mapper.WorkLogMapper;

import lombok.RequiredArgsConstructor;

/**
 * 입금 거래를 PLATFORM/JOB과 매칭해 SETTLEMENT를 생성하고, 해당 기간의 확정된(CONFIRMED)
 * WORK_LOG를 COMPLETED로 갱신한다. processDate 당일의 입금 거래만 확인하므로 매일 배치로
 * 호출하는 것을 전제로 한다. 이미 같은 accountTransactionId로 처리된 거래는 재처리하지
 * 않는다 - 배치가 중복 실행돼도 안전하고, 같은 잡·같은 정산기간에 서로 다른 거래가 여러 건
 * 들어와도(거래 ID가 다르므로) 각각 정상적으로 반영된다.
 *
 * <p>매칭되지 않은(UNMATCHED) 거래도 버리지 않고, 같은 날짜에 들어온 것들을 합산해
 * status=UNMATCHED(job_id=null) 행 하나로 저장한다. 한 플랫폼에 회원 잡이 여러 개
 * 매핑되는 경우(AMBIGUOUS)는 없다고 가정하고 별도로 다루지 않는다.</p>
 *
 * <p>ON_DEMAND(포인트 적립 후 사용자가 임의 시점에 출금 신청) platform은 실제 입금액이
 * 특정 근무일과 대응되지 않으므로, 매칭돼도 WorkLog는 건드리지 않고 해당 잡으로 SETTLEMENT만
 * 남긴다. WorkLog.settlementStatus는 확정(CONFIRMED) 시점에 WorkLogService가 이미
 * 즉시 COMPLETED로 처리한다 - 이 배치와는 독립적이다.</p>
 *
 * <p>settlement_offset_unit이 BUSINESS_DAY인 platform(배민커넥트/쿠팡이츠 배달파트너)만
 * HolidayService로 공휴일을 조회해 정산 기간 계산에 반영한다. CALENDAR_DAY 플랫폼은 공휴일
 * 조회 자체를 안 해서 불필요한 DB 조회를 피한다.</p>
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    /** BUSINESS_DAY 역산 시 조회할 공휴일 범위 버퍼(일). offset이 커져도 여유 있게 잡음. */
    private static final int HOLIDAY_LOOKBACK_DAYS = 30;
    private static final String BUSINESS_DAY = "BUSINESS_DAY";

    private final IncomingTransactionQueryClient incomingTransactionQueryClient;
    private final PlatformMapper platformMapper;
    private final JobMapper jobMapper;
    private final JobPlatformMappingMapper jobPlatformMappingMapper;
    private final WorkLogMapper workLogMapper;
    private final SettlementMapper settlementMapper;
    private final HolidayService holidayService;

    /** 이 잡에 매핑된 platform 중 ON_DEMAND(포인트 적립 후 사용자가 출금 신청하는 방식)가 있는지. */
    public boolean isOnDemandJob(Long jobId) {
        return jobPlatformMappingMapper.findByJobId(jobId).stream()
                .map(mapping -> platformMapper.findById(mapping.getPlatformId()))
                .anyMatch(platform -> "ON_DEMAND".equals(platform.getSettlementTriggerType()));
    }

    public void processSettlement(Long userId, LocalDate processDate) {
        List<NormalizedIncomingTransaction> transactions =
                incomingTransactionQueryClient.findIncomingTransactions(userId, processDate, processDate);
        if (transactions.isEmpty()) {
            return;
        }

        List<Platform> platforms = platformMapper.findAll();
        List<Job> jobs = jobMapper.findByUserId(userId);

        long unmatchedAmount = 0;
        int unmatchedCount = 0;
        for (NormalizedIncomingTransaction transaction : transactions) {
            if (!processMatchedTransaction(userId, transaction, platforms, jobs)) {
                unmatchedAmount += transaction.amount().longValueExact();
                unmatchedCount++;
            }
        }
        if (unmatchedCount > 0) {
            saveUnmatchedSettlement(userId, processDate, unmatchedAmount, unmatchedCount);
        }
    }

    /** 매칭에 성공해 SETTLEMENT를 만들었거나 이미 이 거래로 만들어져 있으면 true. */
    private boolean processMatchedTransaction(Long userId, NormalizedIncomingTransaction transaction,
                                               List<Platform> platforms, List<Job> jobs) {
        PlatformMatchResult result = PlatformMatcher.match(transaction.counterpartyName(), platforms);
        if (!(result instanceof PlatformMatchResult.Matched matched)) {
            return false;
        }
        Platform platform = matched.platform();
        Long jobId = resolveJobId(platform.getPlatformId(), jobs);
        if (jobId == null) {
            return false;
        }

        if (settlementMapper.existsByAccountTransactionId(transaction.transactionId())) {
            return true;
        }

        if ("ON_DEMAND".equals(platform.getSettlementTriggerType())) {
            saveOnDemandSettlement(userId, jobId, transaction);
            return true;
        }

        Set<LocalDate> holidays = BUSINESS_DAY.equals(platform.getSettlementOffsetUnit())
                ? holidayService.getHolidays(
                        transaction.transactionDate().minusDays(HOLIDAY_LOOKBACK_DAYS), transaction.transactionDate())
                : Set.of();
        SettlementPeriod period = SettlementPeriodCalculator.calculate(
                platform, transaction.transactionDate(), holidays);
        List<WorkLog> logsInPeriod = findConfirmedLogsInPeriod(jobId, period);
        long expectedAmount = logsInPeriod.stream()
                .mapToLong(log -> log.getEstimatedIncome() == null ? 0L : log.getEstimatedIncome())
                .sum();

        Settlement settlement = Settlement.builder()
                .userId(userId)
                .status(SettlementMatchStatus.MATCHED)
                .jobId(jobId)
                .periodStart(period.start())
                .periodEnd(period.end())
                .depositDate(transaction.transactionDate())
                .expectedAmount(expectedAmount)
                .actualAmount(transaction.amount().longValueExact())
                .transactionCount(1)
                .accountTransactionId(transaction.transactionId())
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);

        for (WorkLog log : logsInPeriod) {
            log.setSettlementStatus(SettlementStatus.COMPLETED);
            workLogMapper.update(log);
        }
        return true;
    }

    /**
     * ON_DEMAND는 실제 입금액이 특정 근무일과 대응되지 않으므로(사용자가 임의 시점에 임의
     * 금액을 출금 신청) 근무일지는 건드리지 않고, 해당 잡으로만 SETTLEMENT를 남긴다.
     * WorkLog.settlementStatus는 이미 확정(CONFIRMED) 시점에 WorkLogService가 즉시
     * COMPLETED로 처리해뒀다 (이 메서드와 독립적).
     */
    private void saveOnDemandSettlement(Long userId, Long jobId, NormalizedIncomingTransaction transaction) {
        Settlement settlement = Settlement.builder()
                .userId(userId)
                .status(SettlementMatchStatus.MATCHED)
                .jobId(jobId)
                .periodStart(transaction.transactionDate())
                .periodEnd(transaction.transactionDate())
                .depositDate(transaction.transactionDate())
                .expectedAmount(0L)
                .actualAmount(transaction.amount().longValueExact())
                .transactionCount(1)
                .accountTransactionId(transaction.transactionId())
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);
    }

    /**
     * 매칭되지 않은 거래는 잡을 특정할 수 없으니 job_id=null로, 같은 날짜 것들을 합쳐
     * 하루 단위 한 행으로 저장한다. 배치가 중복 실행돼도 같은 날짜에 두 번 쌓이지 않도록
     * UNMATCHED는 (user_id, status, period) 기준으로 존재 여부를 확인한다.
     */
    private void saveUnmatchedSettlement(Long userId, LocalDate processDate, long amount, int count) {
        if (settlementMapper.existsByUserIdAndStatusAndPeriod(
                userId, SettlementMatchStatus.UNMATCHED, processDate, processDate)) {
            return;
        }

        Settlement settlement = Settlement.builder()
                .userId(userId)
                .status(SettlementMatchStatus.UNMATCHED)
                .jobId(null)
                .periodStart(processDate)
                .periodEnd(processDate)
                .depositDate(processDate)
                .expectedAmount(0L)
                .actualAmount(amount)
                .transactionCount(count)
                .accountTransactionId(null)
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);
    }

    private Long resolveJobId(Long platformId, List<Job> jobs) {
        for (Job job : jobs) {
            boolean mapped = jobPlatformMappingMapper.findByJobId(job.getJobId()).stream()
                    .anyMatch(mapping -> mapping.getPlatformId().equals(platformId));
            if (mapped) {
                return job.getJobId();
            }
        }
        return null;
    }

    private List<WorkLog> findConfirmedLogsInPeriod(Long jobId, SettlementPeriod period) {
        return workLogMapper.findByJobId(jobId).stream()
                .filter(log -> "CONFIRMED".equals(log.getStatus()))
                .filter(log -> log.getWorkDate() != null)
                .filter(log -> !log.getWorkDate().isBefore(period.start())
                        && !log.getWorkDate().isAfter(period.end()))
                .toList();
    }
}
