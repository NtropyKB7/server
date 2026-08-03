package com.ntropy.common.dto.work.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlannedWorkIncomeSummary {
    private Long workScheduleId;
    private LocalDate workDate;
    private Long expectedIncome;
}
