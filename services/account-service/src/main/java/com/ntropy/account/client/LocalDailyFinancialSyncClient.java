package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.domain.InstanceOwnerId;
import com.ntropy.account.service.BatchExecutionLeaseService;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.account.service.DailyCodefSyncService;
import com.ntropy.account.service.DailyNtropySyncService;
import com.ntropy.common.client.DailyFinancialSyncClient;
import com.ntropy.common.domain.DailyFinancialSyncProvider;
import com.ntropy.common.dto.account.DailyFinancialSyncResult;

import lombok.RequiredArgsConstructor;

/**
 * {@link DailyFinancialSyncClient}의 account-service 구현체 (이슈 #158).
 * provider별 job_name으로 lease를 획득하고, 성공적으로 획득했을 때만 실제 동기화를 실행한다.
 * 이미 다른 인스턴스가 유효한 lease를 쥐고 있으면(같은 job_name/business_date 동시 실행) 아무 것도
 * 하지 않고 executionStatus="SKIPPED"를 반환한다. 동기화가 끝나면 그 결과로 lease를 완료 처리한다.
 */
@Component
@RequiredArgsConstructor
public class LocalDailyFinancialSyncClient implements DailyFinancialSyncClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BatchExecutionLeaseService leaseService;
    private final DailyCodefSyncService codefSyncService;
    private final DailyNtropySyncService ntropySyncService;

    private final String ownerId = InstanceOwnerId.generate();

    @Override
    public DailyFinancialSyncResult synchronize(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                                LocalDate businessDate) {
        String jobName = jobNameFor(provider);
        Optional<LeaseHandle> lease = leaseService.acquire(jobName, businessDate, ownerId);
        if (lease.isEmpty()) {
            return skippedResult(provider, businessDate);
        }

        DailyFinancialSyncResult result = executeSync(provider, activeUserIds, businessDate, lease.get());
        leaseService.complete(lease.get(), toBatchExecutionStatus(result.executionStatus()), errorSummary(result));
        return result;
    }

    private DailyFinancialSyncResult executeSync(DailyFinancialSyncProvider provider, List<Long> activeUserIds,
                                                 LocalDate businessDate, LeaseHandle lease) {
        return switch (provider) {
            case CODEF -> codefSyncService.synchronize(activeUserIds, businessDate, lease);
            case NTROPY -> ntropySyncService.synchronize(activeUserIds, businessDate, lease);
        };
    }

    private static String jobNameFor(DailyFinancialSyncProvider provider) {
        return switch (provider) {
            case CODEF -> DailyCodefSyncService.JOB_NAME;
            case NTROPY -> DailyNtropySyncService.JOB_NAME;
        };
    }

    private static BatchExecutionStatus toBatchExecutionStatus(String executionStatus) {
        return switch (executionStatus) {
            case "SUCCESS" -> BatchExecutionStatus.SUCCESS;
            case "PARTIAL_FAILED" -> BatchExecutionStatus.PARTIAL_FAILED;
            default -> BatchExecutionStatus.FAILED;
        };
    }

    /** organizationCode + errorCode만 담는다. connectedId·계좌번호·생년월일 등 민감정보는 포함하지 않는다. */
    private static String errorSummary(DailyFinancialSyncResult result) {
        List<Map<String, Object>> failures = result.institutionResults().stream()
                .filter(institution -> !"SUCCESS".equals(institution.status()))
                .map(institution -> Map.<String, Object>of(
                        "organizationCode", institution.organizationCode(),
                        "errorCode", institution.errorCode() == null ? "" : institution.errorCode()
                ))
                .toList();
        if (failures.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("failures", failures));
        } catch (Exception e) {
            throw new IllegalStateException("오류 요약 직렬화 실패", e);
        }
    }

    private static DailyFinancialSyncResult skippedResult(DailyFinancialSyncProvider provider, LocalDate businessDate) {
        return new DailyFinancialSyncResult(
                businessDate, provider, "SKIPPED", List.of(), Map.of(), List.of(), List.of(), 0
        );
    }
}
