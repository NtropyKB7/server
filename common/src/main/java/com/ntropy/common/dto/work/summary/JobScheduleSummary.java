package com.ntropy.common.dto.work.summary;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobScheduleSummary {

    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
}
