package com.ntropy.work.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSchedule {

    private Long scheduleId;
    private Long jobId;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
}
