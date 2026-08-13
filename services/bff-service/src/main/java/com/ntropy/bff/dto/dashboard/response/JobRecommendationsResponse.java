package com.ntropy.bff.dto.dashboard.response;

import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.common.dto.work.summary.RecommendedWorkHoursSummary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobRecommendationsResponse {

    private String targetMonth;
    private Long totalRecommendedHours;
    private List<JobRecommendationResponse> jobs;

    private JobRecommendationsResponse(String targetMonth, Long totalRecommendedHours, List<JobRecommendationResponse> jobs) {
        this.targetMonth = targetMonth;
        this.totalRecommendedHours = totalRecommendedHours;
        this.jobs = jobs;
    }

    /** 이번 달 저축목표가 없으면 null. */
    public static JobRecommendationsResponse from(RecommendedWorkHoursSummary summary) {
        if (summary == null) {
            return null;
        }
        List<JobRecommendationResponse> jobs = summary.getRecommendedJobs().stream()
                .map(JobRecommendationResponse::from)
                .collect(Collectors.toList());
        return new JobRecommendationsResponse(summary.getTargetMonth(), summary.getTotalRecommendedHours(), jobs);
    }
}
