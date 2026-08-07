package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.client.IncomingTransactionQueryClient;
import com.ntropy.common.dto.account.internal.NormalizedIncomingTransaction;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobPlatformMapping;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementMatchStatus;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobPlatformMappingMapper;
import com.ntropy.work.mapper.InMemoryPlatformMapper;
import com.ntropy.work.mapper.InMemorySettlementMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;

class SettlementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_ID = 100L;
    private static final Long PLATFORM_ID = 10L;
    private static final LocalDate PROCESS_DATE = LocalDate.of(2026, 8, 10);
    private static final LocalDate PERIOD_DATE = PROCESS_DATE.minusDays(1); // DAILY offset=1

    private final InMemoryPlatformMapper platformMapper = new InMemoryPlatformMapper();
    private final InMemoryJobMapper jobMapper = new InMemoryJobMapper();
    private final InMemoryJobPlatformMappingMapper jobPlatformMappingMapper = new InMemoryJobPlatformMappingMapper();
    private final InMemoryWorkLogMapper workLogMapper = new InMemoryWorkLogMapper();
    private final InMemorySettlementMapper settlementMapper = new InMemorySettlementMapper();
    private StubIncomingTransactionQueryClient incomingTransactionQueryClient;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        platformMapper.seed(Platform.builder()
                .platformId(PLATFORM_ID)
                .depositName("테스트플랫폼")
                .settlementCycle("DAILY")
                .settlementOffsetDay(1)
                .build());
        jobMapper.seed(Job.builder().jobId(JOB_ID).userId(USER_ID).build());
        incomingTransactionQueryClient = new StubIncomingTransactionQueryClient();
        service = new SettlementService(
                incomingTransactionQueryClient, platformMapper, jobMapper, jobPlatformMappingMapper,
                workLogMapper, settlementMapper);
    }

    @Test
    @DisplayName("입금 거래가 매칭되면 MATCHED SETTLEMENT가 생성되고 기간 내 CONFIRMED 로그만 COMPLETED로 갱신된다")
    void processSettlement_matched_createsSettlementAndUpdatesConfirmedLogsOnly() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        WorkLog inPeriodConfirmed = workLog(PERIOD_DATE, "CONFIRMED", 50_000L);
        WorkLog outOfPeriodConfirmed = workLog(PERIOD_DATE.minusDays(1), "CONFIRMED", 30_000L);
        WorkLog inPeriodPlanned = workLog(PERIOD_DATE, "PLANNED", 20_000L);
        workLogMapper.insert(inPeriodConfirmed);
        workLogMapper.insert(outOfPeriodConfirmed);
        workLogMapper.insert(inPeriodPlanned);
        incomingTransactionQueryClient.transactions = List.of(transaction(999L, 48_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        Settlement settlement = settlements.get(0);
        assertEquals(SettlementMatchStatus.MATCHED, settlement.getStatus());
        assertEquals(USER_ID, settlement.getUserId());
        assertEquals(JOB_ID, settlement.getJobId());
        assertEquals(PERIOD_DATE, settlement.getPeriodStart());
        assertEquals(PERIOD_DATE, settlement.getPeriodEnd());
        assertEquals(PROCESS_DATE, settlement.getDepositDate());
        assertEquals(50_000L, settlement.getExpectedAmount());
        assertEquals(48_000L, settlement.getActualAmount());
        assertEquals(1, settlement.getTransactionCount());
        assertEquals(999L, settlement.getAccountTransactionId());

        assertEquals(SettlementStatus.COMPLETED, workLogMapper.findById(inPeriodConfirmed.getLogId()).getSettlementStatus());
        assertEquals(SettlementStatus.NONE, workLogMapper.findById(outOfPeriodConfirmed.getLogId()).getSettlementStatus());
        assertEquals(SettlementStatus.NONE, workLogMapper.findById(inPeriodPlanned.getLogId()).getSettlementStatus());
    }

    @Test
    @DisplayName("PLATFORM과 매칭되지 않는 거래는 job_id=null인 UNMATCHED SETTLEMENT로 합산 저장된다")
    void processSettlement_unmatchedTransaction_savesUnmatchedSettlement() {
        incomingTransactionQueryClient.transactions = List.of(
                new NormalizedIncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처", BigDecimal.valueOf(10_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        Settlement settlement = settlements.get(0);
        assertEquals(SettlementMatchStatus.UNMATCHED, settlement.getStatus());
        assertNull(settlement.getJobId());
        assertEquals(USER_ID, settlement.getUserId());
        assertEquals(10_000L, settlement.getActualAmount());
        assertEquals(1, settlement.getTransactionCount());
        assertEquals(PROCESS_DATE, settlement.getDepositDate());
    }

    @Test
    @DisplayName("같은 날짜의 UNMATCHED 거래 여러 건은 하나의 SETTLEMENT 행으로 합산된다")
    void processSettlement_multipleUnmatchedTransactionsSameDay_aggregatesIntoOneRow() {
        incomingTransactionQueryClient.transactions = List.of(
                new NormalizedIncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처1", BigDecimal.valueOf(10_000L)),
                new NormalizedIncomingTransaction(2L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처2", BigDecimal.valueOf(5_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        assertEquals(15_000L, settlements.get(0).getActualAmount());
        assertEquals(2, settlements.get(0).getTransactionCount());
    }

    @Test
    @DisplayName("매칭되는 PLATFORM이지만 사용자가 JOB으로 등록하지 않았으면 UNMATCHED로 저장된다")
    void processSettlement_platformNotRegisteredAsJob_savesUnmatchedSettlement() {
        incomingTransactionQueryClient.transactions = List.of(transaction(1L, 10_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        List<Settlement> settlements = settlementMapper.findAll();
        assertEquals(1, settlements.size());
        assertEquals(SettlementMatchStatus.UNMATCHED, settlements.get(0).getStatus());
    }

    @Test
    @DisplayName("이미 같은 job+기간의 MATCHED SETTLEMENT가 있으면 중복 생성하지 않는다")
    void processSettlement_alreadySettled_skipsDuplicate() {
        jobPlatformMappingMapper.insert(JobPlatformMapping.builder().jobId(JOB_ID).platformId(PLATFORM_ID).build());
        settlementMapper.insert(Settlement.builder()
                .userId(USER_ID).status(SettlementMatchStatus.MATCHED)
                .jobId(JOB_ID).periodStart(PERIOD_DATE).periodEnd(PERIOD_DATE)
                .depositDate(PROCESS_DATE.minusDays(1))
                .expectedAmount(0L).actualAmount(0L).transactionCount(1).accountTransactionId(1L)
                .matchedAt(java.time.LocalDateTime.now())
                .build());
        incomingTransactionQueryClient.transactions = List.of(transaction(999L, 48_000L));

        service.processSettlement(USER_ID, PROCESS_DATE);

        assertEquals(1, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("이미 같은 날짜의 UNMATCHED SETTLEMENT가 있으면 중복 생성하지 않는다")
    void processSettlement_alreadyUnmatchedSettled_skipsDuplicate() {
        settlementMapper.insert(Settlement.builder()
                .userId(USER_ID).status(SettlementMatchStatus.UNMATCHED)
                .jobId(null).periodStart(PROCESS_DATE).periodEnd(PROCESS_DATE)
                .depositDate(PROCESS_DATE)
                .expectedAmount(0L).actualAmount(5_000L).transactionCount(1).accountTransactionId(null)
                .matchedAt(java.time.LocalDateTime.now())
                .build());
        incomingTransactionQueryClient.transactions = List.of(
                new NormalizedIncomingTransaction(1L, PROCESS_DATE, LocalTime.NOON, "알수없는입금처", BigDecimal.valueOf(10_000L))
        );

        service.processSettlement(USER_ID, PROCESS_DATE);

        assertEquals(1, settlementMapper.findAll().size());
    }

    private static WorkLog workLog(LocalDate workDate, String status, long estimatedIncome) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(JOB_ID)
                .workDate(workDate)
                .estimatedIncome(estimatedIncome)
                .status(status)
                .fatigue(0L)
                .settlementStatus(SettlementStatus.NONE)
                .build();
    }

    private static NormalizedIncomingTransaction transaction(long transactionId, long amount) {
        return new NormalizedIncomingTransaction(
                transactionId, PROCESS_DATE, LocalTime.NOON, "테스트플랫폼", BigDecimal.valueOf(amount));
    }

    private static final class StubIncomingTransactionQueryClient implements IncomingTransactionQueryClient {
        private List<NormalizedIncomingTransaction> transactions = List.of();

        @Override
        public List<NormalizedIncomingTransaction> findIncomingTransactions(
                Long userId, LocalDate startDate, LocalDate endDate) {
            return transactions;
        }
    }
}
