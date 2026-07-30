package com.ntropy.bff.dto.work.request;

import com.ntropy.common.dto.work.JobUpdateCommand;

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

    public JobUpdateCommand toCommand() {
        return new JobUpdateCommand(
                categoryId, jobName, settlementType, hourlyWage,
                monthlyWage, perTaskWage, taskPerHour, isRegular, baseFatigue
        );
    }
}
