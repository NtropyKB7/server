package com.ntropy.bff.dto.work.response;

import com.ntropy.common.dto.work.summary.JobSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JOB은 급여/정산 정보를 담고 있어 common의 JobSummary를 그대로 노출하지 않고
 * 필드 단위로 감싼다. work-service 내부 사정으로 JobSummary가 바뀌어도
 * 프론트 응답 계약은 이 클래스가 그대로 지켜준다.
 */
@Getter
@NoArgsConstructor
public class JobResponse {

    private Long jobId;
    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
    private Boolean isActive;

    public static JobResponse from(JobSummary summary) {
        JobResponse response = new JobResponse();
        response.jobId = summary.getJobId();
        response.categoryId = summary.getCategoryId();
        response.jobName = summary.getJobName();
        response.settlementType = summary.getSettlementType();
        response.hourlyWage = summary.getHourlyWage();
        response.monthlyWage = summary.getMonthlyWage();
        response.perTaskWage = summary.getPerTaskWage();
        response.taskPerHour = summary.getTaskPerHour();
        response.isRegular = summary.getIsRegular();
        response.baseFatigue = summary.getBaseFatigue();
        response.isActive = summary.getIsActive();
        return response;
    }
}
