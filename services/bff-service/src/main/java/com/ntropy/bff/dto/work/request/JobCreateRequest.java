package com.ntropy.bff.dto.work.request;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ntropy.common.dto.work.command.JobRegisterCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobCreateRequest {

    /**
     * 사용자 입력 미허용, PER_TASK 잡의 시간당 예상 처리 건수는 카테고리별 기본값으로 고정한다
     * (2026-08). 매핑에 없는 카테고리(택배/물류 상하차, 펫시터·돌봄, 콘텐츠 제작)는 PER_TASK로
     * 등록하지 않을 것으로 보여 값을 지어내지 않고 null로 둔다 - JobService의 예상소득 계산은
     * taskPerHour가 null이면 예상소득만 null로 내려가고 등록 자체는 그대로 진행된다.
     */
    private static final Map<Long, Float> DEFAULT_TASK_PER_HOUR_BY_CATEGORY = Map.of(
            1L, 3.5f,   // 배달
            2L, 1.5f,   // 대리운전
            4L, 0.25f   // 가사·청소 도우미
    );

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
                "PER_TASK".equals(settlementType) ? DEFAULT_TASK_PER_HOUR_BY_CATEGORY.get(categoryId) : null,
                isRegular,
                baseFatigue,
                platformIds,
                safeSchedules.stream()
                        .map(JobScheduleRequest::toCommand)
                        .collect(Collectors.toList())
        );
    }
}
