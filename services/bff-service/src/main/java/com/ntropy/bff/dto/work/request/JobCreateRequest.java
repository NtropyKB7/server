package com.ntropy.bff.dto.work.request;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.common.dto.work.JobRegisterCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobCreateRequest {

    private Long userId;
    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
    private List<Long> platformIds;
    private List<JobScheduleRequest> schedules;

    public JobRegisterCommand toCommand() {
        List<JobScheduleRequest> safeSchedules = schedules == null ? Collections.emptyList() : schedules;
        return new JobRegisterCommand(
                userId,
                categoryId,
                jobName,
                settlementType,
                hourlyWage,
                monthlyWage,
                perTaskWage,
                taskPerHour,
                isRegular,
                baseFatigue,
                platformIds,
                safeSchedules.stream()
                        .map(JobScheduleRequest::toCommand)
                        .collect(Collectors.toList())
        );
    }
}
