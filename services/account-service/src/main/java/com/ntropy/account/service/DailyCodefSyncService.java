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
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.IncrementalSyncRangeCalculator;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;
import com.ntropy.account.service.AccountCollectionService.AccountCollectionOutcome;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.common.domain.DailyFinancialSyncProvider;
import com.ntropy.common.dto.account.DailyFinancialSyncResult;
import com.ntropy.common.dto.account.DailyFinancialSyncResult.InstitutionSyncResult;

import lombok.RequiredArgsConstructor;

/**
 * {@code provider=CODEF} 연결의 일일 증분 동기화를 조합한다 (이슈 #158).
 * 사용자·기관·계좌 단위로 실패를 격리하며, 기관 watermark(ACCOUNT_SYNC_STATE) 전진과 heartbeat는
 * {@link BatchExecutionLeaseService}가 부여한 lease의 fencing을 통과할 때만 이뤄진다.
 * lease를 잃으면(heartbeat 또는 watermark 갱신 실패) 남은 처리를 즉시 중단한다.
 */
@Service
@RequiredArgsConstructor
public class DailyCodefSyncService {

    public static final String JOB_NAME = "daily-sync-codef";

    private final CodefConnectionMapper codefConnectionMapper;
    private final AccountSyncStateMapper accountSyncStateMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final AccountCollectionService accountCollectionService;
    private final BatchExecutionLeaseService leaseService;
    private final BirthDateCipher birthDateCipher;
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
            CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
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

                PersonalBank bank;
                try {
                    bank = PersonalBank.fromOrganizationCode(organizationCode);
                } catch (IllegalArgumentException unsupportedOrganization) {
                    continue; // 지원 대상 밖 기관코드 방어
                }

                InstitutionSyncOutcome outcome = synchronizeInstitution(userId, bank, connection, businessDate);
                processedTransactionCount += outcome.transactionCount();
                institutionAggregates.merge(organizationCode, outcome.aggregate(), InstitutionAggregate::merge);

                if (outcome.aggregate().hasFailure()) {
                    userHasFailure = true;
                    continue; // watermark 전진하지 않음
                }

                LocalDateTime now = LocalDateTime.now(clock);
                boolean advanced = accountSyncStateMapper.advanceIfOwner(
                        connection.getId(), organizationCode, now, outcome.watermarkStatus(), null,
                        lease.jobName(), lease.businessDate(), lease.ownerId(), lease.leaseToken(), now
                ) == 1;
                if (!advanced) {
                    leaseLost = true;
                    break userLoop;
                }
                if (outcome.hadAnySuccess()) {
                    userHasSuccess = true;
                    affectedYearMonthsByUser
                            .computeIfAbsent(userId, id -> new TreeSet<>())
                            .addAll(monthsBetween(outcome.startDate(), businessDate));
                }
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
                DailyFinancialSyncProvider.CODEF,
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

    private InstitutionSyncOutcome synchronizeInstitution(Long userId, PersonalBank bank, CodefConnection connection,
                                                           LocalDate businessDate) {
        String organizationCode = bank.getOrganizationCode();

        String birthDate = null;
        if (bank.isBirthDateRequired()) {
            if (connection.getBirthDateCiphertext() == null || connection.getBirthDateIv() == null) {
                return InstitutionSyncOutcome.failed(InstitutionAggregate.failed("CREDENTIAL_MISSING"));
            }
            birthDate = birthDateCipher.decrypt(connection.getBirthDateCiphertext(), connection.getBirthDateIv());
        }

        AccountSyncState state = accountSyncStateMapper.findByConnectionAndOrganization(connection.getId(), organizationCode);
        LocalDate mostRecentStoredDate =
                accountTransactionMapper.findMostRecentTransactionDate(connection.getId(), organizationCode);
        LocalDate startDate = IncrementalSyncRangeCalculator.startDate(
                state == null ? null : state.getLastSuccessfulSyncedAt(),
                mostRecentStoredDate, businessDate, incrementalSyncPolicy
        );

        List<AccountCollectionOutcome> outcomes;
        try {
            outcomes = accountCollectionService.collectForDailySync(userId, bank, birthDate, startDate, businessDate);
        } catch (RuntimeException e) {
            return InstitutionSyncOutcome.failed(InstitutionAggregate.failed("COLLECTION_FAILED"));
        }

        boolean hasRealFailure = outcomes.stream()
                .anyMatch(o -> o.status() == AccountCollectionOutcome.Status.FAILED);
        boolean hasSuccess = outcomes.stream()
                .anyMatch(o -> o.status() == AccountCollectionOutcome.Status.SUCCESS);
        long transactionCount = outcomes.stream().mapToLong(AccountCollectionOutcome::transactionCount).sum();

        if (hasRealFailure) {
            return new InstitutionSyncOutcome(
                    InstitutionAggregate.failed("COLLECTION_FAILED"), false, startDate, transactionCount, null
            );
        }
        return new InstitutionSyncOutcome(
                InstitutionAggregate.success(), hasSuccess, startDate, transactionCount, "SUCCESS"
        );
    }

    /**
     * 조회 구간에 걸친 연월 목록. {@code affectedYearMonthsByUser}는 "정확히 신규/변경된 거래"가 아니라
     * 조회되어 멱등 upsert에 성공한 거래가 속할 수 있는 연월(MVP 단순화)로 정의한다.
     */
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

    private record InstitutionSyncOutcome(InstitutionAggregate aggregate, boolean hadAnySuccess,
                                          LocalDate startDate, long transactionCount, String watermarkStatus) {
        static InstitutionSyncOutcome failed(InstitutionAggregate aggregate) {
            return new InstitutionSyncOutcome(aggregate, false, null, 0, null);
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
