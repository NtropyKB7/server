package com.ntropy.bff.dto.work.request;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.common.dto.work.command.JobRegisterCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobCreateRequest {

    /** 사용자 입력 미허용, PER_TASK 잡의 시간당 예상 처리 건수는 3건으로 고정한다 (추후 변경 예정). */
    private static final float FIXED_TASK_PER_HOUR = 3f;

    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Boolean isRegular;
    private Integer baseFatigue;
    private List<Long> platformIds;
    private List<JobScheduleRequest> schedules;

    public JobRegisterCommand toCommand(Long userId) {
        List<JobScheduleRequest> safeSchedules = schedules == null ? Collections.emptyList() : schedules;
        return new JobRegisterCommand(
                userId,
                categoryId,
                jobName,
                settlementType,
                hourlyWage,
                monthlyWage,
                perTaskWage,
                "PER_TASK".equals(settlementType) ? FIXED_TASK_PER_HOUR : null,
                isRegular,
                baseFatigue,
                platformIds,
                safeSchedules.stream()
                        .map(JobScheduleRequest::toCommand)
                        .collect(Collectors.toList())
        );
    }
}
