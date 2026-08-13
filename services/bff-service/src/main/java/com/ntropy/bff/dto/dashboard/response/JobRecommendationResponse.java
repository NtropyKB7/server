package com.ntropy.bff.dto.dashboard.response;

import com.ntropy.common.dto.work.summary.RecommendedJobHoursSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobRecommendationResponse {

    private Long jobId;
    private String jobName;
    private Long recommendedHours;
    private Long expectedIncome;
    private Integer baseFatigue;

    public static JobRecommendationResponse from(RecommendedJobHoursSummary summary) {
        return new JobRecommendationResponse(
                summary.getJobId(),
                summary.getJobName(),
                summary.getRecommendedHours(),
                summary.getExpectedIncome(),
                summary.getBaseFatigue()
        );
    }
}
