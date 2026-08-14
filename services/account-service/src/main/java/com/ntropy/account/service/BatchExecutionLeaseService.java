package com.ntropy.account.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ntropy.account.config.DailySyncLeaseProperties;
import com.ntropy.account.domain.BatchExecutionStatus;
import com.ntropy.account.domain.entity.DailyBatchExecution;
import com.ntropy.account.mapper.DailyBatchExecutionMapper;

import lombok.RequiredArgsConstructor;

/**
 * {@code DAILY_BATCH_EXECUTION} 기반 분산 lease를 관리한다 (이슈 #158).
 * 별도 heartbeat 스레드를 두지 않으며, 호출자가 사용자·기관 단위 처리 루프마다
 * {@link #heartbeat(LeaseHandle)}을 호출해 lease_until을 연장하는 것을 전제로 한다.
 * heartbeat·완료는 owner_id + lease_token 기반 fencing으로 보호되므로, lease를 이미 잃은
 * 호출자가 실행 상태를 덮어쓸 수 없다.
 */
@Service
@RequiredArgsConstructor
public class BatchExecutionLeaseService {

    private final DailyBatchExecutionMapper dailyBatchExecutionMapper;
    private final DailySyncLeaseProperties leaseProperties;
    private final Clock clock;

    /**
     * lease 획득을 시도한다. INSERT가 UNIQUE(job_name, business_date) 위반으로 실패하면
     * 만료됐거나 완료된 기존 실행에 대해 조건부 UPDATE를 시도하고, 영향받은 row 수가 1이 아니면
     * 다른 인스턴스가 이미 선점한 것으로 보고 빈 Optional을 반환한다.
     */
    public Optional<LeaseHandle> acquire(String jobName, LocalDate businessDate, String ownerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String leaseToken = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plus(leaseProperties.getLeaseDuration());

        DailyBatchExecution execution = new DailyBatchExecution();
        execution.setJobName(jobName);
        execution.setBusinessDate(businessDate);
        execution.setStatus(BatchExecutionStatus.RUNNING);
        execution.setOwnerId(ownerId);
        execution.setLeaseToken(leaseToken);
        execution.setLeaseUntil(leaseUntil);
        execution.setStartedAt(now);

        try {
            dailyBatchExecutionMapper.insert(execution);
            return Optional.of(new LeaseHandle(execution.getId(), jobName, businessDate, ownerId, leaseToken));
        } catch (DuplicateKeyException alreadyExists) {
            int acquired = dailyBatchExecutionMapper.acquireExpiredLease(execution, now);
            if (acquired != 1) {
                return Optional.empty();
            }
            DailyBatchExecution takenOver =
                    dailyBatchExecutionMapper.findByJobNameAndBusinessDate(jobName, businessDate);
            return Optional.of(new LeaseHandle(takenOver.getId(), jobName, businessDate, ownerId, leaseToken));
        }
    }

    /**
     * heartbeat. 영향받은 row 수가 0이면 소유권을 잃은 것이므로 {@code false}를 반환한다.
     * 호출자는 {@code false}를 받으면 watermark 갱신을 포함한 남은 처리를 즉시 중단해야 한다.
     */
    public boolean heartbeat(LeaseHandle lease) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime newLeaseUntil = now.plus(leaseProperties.getLeaseDuration());
        int renewed = dailyBatchExecutionMapper.renewLease(
                lease.executionId(), lease.ownerId(), lease.leaseToken(), newLeaseUntil, now
        );
        return renewed == 1;
    }

    /**
     * 완료 처리(SUCCESS/PARTIAL_FAILED/FAILED). heartbeat와 동일한 fencing 조건을 쓰므로,
     * 이미 소유권을 잃은 뒤 호출하면 {@code false}가 반환되고 실행 상태는 바뀌지 않는다.
     */
    public boolean complete(LeaseHandle lease, BatchExecutionStatus status, String errorSummaryJson) {
        LocalDateTime now = LocalDateTime.now(clock);
        int completed = dailyBatchExecutionMapper.completeIfOwner(
                lease.executionId(), lease.ownerId(), lease.leaseToken(),
                status.name(), now, errorSummaryJson, now
        );
        return completed == 1;
    }

    /** 획득한 lease를 식별하는 값 객체. 이후 heartbeat/complete 호출에 그대로 넘긴다. */
    public record LeaseHandle(Long executionId, String jobName, LocalDate businessDate,
                               String ownerId, String leaseToken) {
    }
}
