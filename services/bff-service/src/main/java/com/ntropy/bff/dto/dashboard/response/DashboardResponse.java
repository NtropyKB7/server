package com.ntropy.bff.dto.dashboard.response;

import com.ntropy.common.dto.user.UserSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 홈 대시보드 응답. 필드는 각 도메인의 조회 계약(FatigueQueryClient, DiagnosisQueryClient 등)이
 * 붙는 대로 하나씩 늘어난다. 계약이 없는 필드는 임의 값을 채우지 않고 아예 비워둔다.
 */
@Getter
@AllArgsConstructor
public class DashboardResponse {

    private String greetingName;

    public static DashboardResponse from(UserSummary summary) {
        return new DashboardResponse(summary.name());
    }
}
