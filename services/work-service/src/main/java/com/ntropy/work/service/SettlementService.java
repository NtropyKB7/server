package com.ntropy.work.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
 * 호출하는 것을 전제로 한다. 이미 매칭된 job+기간은 SETTLEMENT UNIQUE 제약으로 재처리하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final IncomingTransactionQueryClient incomingTransactionQueryClient;
    private final PlatformMapper platformMapper;
    private final JobMapper jobMapper;
    private final JobPlatformMappingMapper jobPlatformMappingMapper;
    private final WorkLogMapper workLogMapper;
    private final SettlementMapper settlementMapper;

    public void processSettlement(Long userId, LocalDate processDate) {
        List<NormalizedIncomingTransaction> transactions =
                incomingTransactionQueryClient.findIncomingTransactions(userId, processDate, processDate);
        if (transactions.isEmpty()) {
            return;
        }

        List<Platform> platforms = platformMapper.findAll();
        List<Job> jobs = jobMapper.findByUserId(userId);

        for (NormalizedIncomingTransaction transaction : transactions) {
            processTransaction(transaction, platforms, jobs);
        }
    }

    private void processTransaction(NormalizedIncomingTransaction transaction, List<Platform> platforms,
                                     List<Job> jobs) {
        PlatformMatchResult result = PlatformMatcher.match(transaction.counterpartyName(), platforms);
        if (!(result instanceof PlatformMatchResult.Matched matched)) {
            return;
        }
        Platform platform = matched.platform();
        Long jobId = resolveJobId(platform.getPlatformId(), jobs);
        if (jobId == null) {
            return;
        }

        SettlementPeriod period = SettlementPeriodCalculator.calculate(platform, transaction.transactionDate());
        if (settlementMapper.existsByJobIdAndPeriod(jobId, period.start(), period.end())) {
            return;
        }

        List<WorkLog> logsInPeriod = findConfirmedLogsInPeriod(jobId, period);
        long expectedAmount = logsInPeriod.stream()
                .mapToLong(log -> log.getEstimatedIncome() == null ? 0L : log.getEstimatedIncome())
                .sum();

        Settlement settlement = Settlement.builder()
                .jobId(jobId)
                .periodStart(period.start())
                .periodEnd(period.end())
                .expectedAmount(expectedAmount)
                .actualAmount(transaction.amount().longValueExact())
                .accountTransactionId(transaction.transactionId())
                .matchedAt(LocalDateTime.now())
                .build();
        settlementMapper.insert(settlement);

        for (WorkLog log : logsInPeriod) {
            log.setSettlementStatus(SettlementStatus.COMPLETED);
            workLogMapper.update(log);
        }
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
