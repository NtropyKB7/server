package com.ntropy.account.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.account.config.IncrementalSyncPolicy;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.IncrementalSyncRangeCalculator;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.common.domain.DailyFinancialSyncProvider;
import com.ntropy.common.dto.account.DailyFinancialSyncResult;
import com.ntropy.common.dto.account.DailyFinancialSyncResult.InstitutionSyncResult;

import lombok.RequiredArgsConstructor;

/**
 * {@code provider=NTROPY} 연결의 일일 증분 가상 거래 생성을 조합한다 (이슈 #158).
 * 외부 호출이 없어 CODEF 경로보다 훨씬 빠르지만, {@code DAILY_BATCH_EXECUTION}의 별도
 * job_name({@value #JOB_NAME})으로 독립 실행하고 heartbeat·watermark fencing 규칙은 동일하게 따른다.
 */
@Service
@RequiredArgsConstructor
public class DailyNtropySyncService {

    public static final String JOB_NAME = "daily-sync-ntropy";
    private static final String ORDINARY_DEPOSIT_TYPE_CODE = "11";

    private final CodefConnectionMapper codefConnectionMapper;
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final AccountSyncStateMapper accountSyncStateMapper;
    private final BatchExecutionLeaseService leaseService;
    private final NtropyIncrementalTransactionGenerator transactionGenerator;
    private final IncrementalSyncPolicy incrementalSyncPolicy;
    private final Clock clock;

    public DailyFinancialSyncResult synchronize(List<Long> activeUserIds, LocalDate businessDate, LeaseHandle lease) {
        Set<Long> successfulUserIds = new LinkedHashSet<>();
        Set<Long> partialFailedUserIds = new LinkedHashSet<>();
        Map<Long, Set<YearMonth>> affectedYearMonthsByUser = new LinkedHashMap<>();
        Map<String, InstitutionAggregate> institutionAggregates = new LinkedHashMap<>();
        long processedTransactionCount = 0;
        boolean leaseLost = false;

        userLoop:
        for (Long userId : activeUserIds) {
            CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name());
            if (connection == null || connection.getConnectedId() == null || connection.getConnectedId().isBlank()) {
                continue; // 이 provider의 동기화 대상이 아닌 사용자
            }

            boolean userHasFailure = false;
            boolean userHasSuccess = false;

            for (String organizationCode : InstitutionKeys.parse(connection.getRegisteredInstitutionKeys())) {
                if (!leaseService.heartbeat(lease)) {
                    leaseLost = true;
                    break userLoop;
                }

                InstitutionGenerationOutcome outcome = generateForInstitution(userId, connection, organizationCode, businessDate);
                processedTransactionCount += outcome.transactionCount();
                institutionAggregates.merge(organizationCode, outcome.aggregate(), InstitutionAggregate::merge);

                if (outcome.aggregate().hasFailure()) {
                    userHasFailure = true;
                    continue;
                }

                LocalDateTime now = LocalDateTime.now(clock);
                boolean advanced = accountSyncStateMapper.advanceIfOwner(
                        connection.getId(), organizationCode, now, "SUCCESS", null,
                        lease.jobName(), lease.businessDate(), lease.ownerId(), lease.leaseToken(), now
                ) == 1;
                if (!advanced) {
                    leaseLost = true;
                    break userLoop;
                }

                userHasSuccess = true;
                affectedYearMonthsByUser
                        .computeIfAbsent(userId, id -> new TreeSet<>())
                        .addAll(monthsBetween(outcome.startDate(), businessDate));
            }

            if (userHasFailure) {
                partialFailedUserIds.add(userId);
            } else if (userHasSuccess) {
                successfulUserIds.add(userId);
            }
        }

        String executionStatus = leaseLost ? "FAILED"
                : partialFailedUserIds.isEmpty() ? "SUCCESS" : "PARTIAL_FAILED";

        return new DailyFinancialSyncResult(
                businessDate,
                DailyFinancialSyncProvider.NTROPY,
                executionStatus,
                List.copyOf(successfulUserIds),
                affectedYearMonthsByUser.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))),
                List.copyOf(partialFailedUserIds),
                institutionAggregates.entrySet().stream()
                        .map(e -> new InstitutionSyncResult(e.getKey(), e.getValue().status(), e.getValue().errorCode()))
                        .toList(),
                processedTransactionCount
        );
    }

    private InstitutionGenerationOutcome generateForInstitution(Long userId, CodefConnection connection,
                                                                 String organizationCode, LocalDate businessDate) {
        Account ordinaryAccount = accountMapper.findByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name()).stream()
                .filter(account -> organizationCode.equals(account.getOrganizationCode()))
                .filter(account -> account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST)
                .filter(account -> ORDINARY_DEPOSIT_TYPE_CODE.equals(account.getDepositTypeCode()))
                .findFirst()
                .orElse(null);
        if (ordinaryAccount == null) {
            return InstitutionGenerationOutcome.failed(InstitutionAggregate.failed("ORDINARY_ACCOUNT_NOT_FOUND"));
        }

        AccountSyncState state = accountSyncStateMapper.findByConnectionAndOrganization(connection.getId(), organizationCode);
        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                state == null ? null : state.getLastSuccessfulSyncedAt(),
                ordinaryAccount.getLastTranDate(), businessDate, incrementalSyncPolicy
        );

        try {
            List<AccountTransaction> transactions = transactionGenerator.generate(
                    userId, ordinaryAccount.getId(), startDate, businessDate
            );
            if (!transactions.isEmpty()) {
                accountTransactionMapper.insertAll(transactions);
            }
            return new InstitutionGenerationOutcome(InstitutionAggregate.success(), startDate, transactions.size());
        } catch (RuntimeException e) {
            return InstitutionGenerationOutcome.failed(InstitutionAggregate.failed("GENERATION_FAILED"));
        }
    }

    private static Set<YearMonth> monthsBetween(LocalDate startDate, LocalDate endDate) {
        Set<YearMonth> months = new TreeSet<>();
        YearMonth cursor = YearMonth.from(startDate);
        YearMonth last = YearMonth.from(endDate);
        while (!cursor.isAfter(last)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private record InstitutionGenerationOutcome(InstitutionAggregate aggregate, LocalDate startDate, long transactionCount) {
        static InstitutionGenerationOutcome failed(InstitutionAggregate aggregate) {
            return new InstitutionGenerationOutcome(aggregate, null, 0);
        }
    }

    private record InstitutionAggregate(boolean hasFailure, String errorCode) {
        static InstitutionAggregate success() {
            return new InstitutionAggregate(false, null);
        }

        static InstitutionAggregate failed(String errorCode) {
            return new InstitutionAggregate(true, errorCode);
        }

        static InstitutionAggregate merge(InstitutionAggregate a, InstitutionAggregate b) {
            if (a.hasFailure || b.hasFailure) {
                return new InstitutionAggregate(true, a.hasFailure ? a.errorCode : b.errorCode);
            }
            return success();
        }

        String status() {
            return hasFailure ? "PARTIAL_FAILED" : "SUCCESS";
        }
    }
}
