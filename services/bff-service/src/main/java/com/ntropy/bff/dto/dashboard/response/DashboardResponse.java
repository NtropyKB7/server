package com.ntropy.bff.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 홈 대시보드 응답. realWage(실질 시급)는 계산 공식이 아직 정의되지 않아 제외한다.
 * jobRecommendations(ROI 추천)는 AI-service의 별도 엔드포인트(/api/dashboard/recommendation-hours) 몫이라 여기 없다.
 */
@Getter
@AllArgsConstructor
public class DashboardResponse {

    private String greetingName;
    private DashboardHoursResponse goalHours;
    private DashboardIncomeResponse goalIncome;
    private Integer fatigueScore;
}
