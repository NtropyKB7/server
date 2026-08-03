package com.ntropy.common.client;

import com.ntropy.common.dto.work.summary.PlannedWorkIncomeSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * work-service가 구현해야 하는 방어모드용 예정 근무소득 조회 계약.
 *
 * <p>요청 기간과 겹치는 예정 근무만 반환하며, 실제 완료 근무는 포함하지 않는다.
 * 방어모드는 반환된 expectedIncome을 합산하여 이번 달 예상 손실 소득을 계산한다.</p>
 */
public interface PlannedWorkIncomeQueryClient {

    /** 인증된 사용자의 기간 내 예정 근무일과 예상 소득을 조회한다. */
    List<PlannedWorkIncomeSummary> findPlannedWorkIncome(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate);
}
