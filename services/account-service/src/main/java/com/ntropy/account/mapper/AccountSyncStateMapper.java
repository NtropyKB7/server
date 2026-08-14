package com.ntropy.account.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.entity.AccountSyncState;

@Mapper
public interface AccountSyncStateMapper {

    AccountSyncState findByConnectionAndOrganization(@Param("codefConnectionId") Long codefConnectionId,
                                                      @Param("organizationCode") String organizationCode);

    /** watermark 행이 없으면 PENDING 상태로 생성한다. 이미 있으면 아무 것도 바꾸지 않는다. */
    void insertIfAbsent(AccountSyncState state);

    /**
     * 호출 시점에 job_name/business_date에 대해 유효한 lease(owner_id + lease_token +
     * status='RUNNING' + lease_until 유효)를 가진 실행에서만 watermark를 전진시킨다 (fencing).
     * 영향받은 row 수가 0이면 watermark 갱신 실패로 처리해야 한다. "현재 시각"은 DB NOW()가 아니라
     * 애플리케이션 Clock으로 계산해 넘긴다 (DB 세션 타임존과 무관하게 동작해야 하므로).
     */
    int advanceIfOwner(@Param("codefConnectionId") Long codefConnectionId,
                       @Param("organizationCode") String organizationCode,
                       @Param("lastSuccessfulSyncedAt") LocalDateTime lastSuccessfulSyncedAt,
                       @Param("lastStatus") String lastStatus,
                       @Param("lastErrorCode") String lastErrorCode,
                       @Param("jobName") String jobName,
                       @Param("businessDate") LocalDate businessDate,
                       @Param("ownerId") String ownerId,
                       @Param("leaseToken") String leaseToken,
                       @Param("now") LocalDateTime now);
}
