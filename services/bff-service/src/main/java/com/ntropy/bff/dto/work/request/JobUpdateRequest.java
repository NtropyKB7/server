package com.ntropy.bff.dto.work.request;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.common.dto.work.command.JobUpdateCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobUpdateRequest {

    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Float taskPerHour;
    private Boolean isRegular;
    private Integer baseFatigue;
    private List<JobScheduleRequest> schedules;
    private List<Long> platformIds;

    public JobUpdateCommand toCommand() {
        List<JobScheduleRequest> safeSchedules = schedules == null ? Collections.emptyList() : schedules;
        return new JobUpdateCommand(
                categoryId, jobName, settlementType, hourlyWage,
                monthlyWage, perTaskWage, taskPerHour, isRegular, baseFatigue,
                safeSchedules.stream()
                        .map(JobScheduleRequest::toCommand)
                        .collect(Collectors.toList()),
                platformIds
        );
    }
}
