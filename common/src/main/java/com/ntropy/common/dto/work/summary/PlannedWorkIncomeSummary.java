package com.ntropy.common.dto.work.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
/** work-service가 방어모드에 전달하는 예정 근무별 예상 소득. */
public class PlannedWorkIncomeSummary {
    /** 예정 근무 식별자. */
    private Long workScheduleId;

    /** 예정 근무일. */
    private LocalDate workDate;

    /** 해당 예정 근무에서 얻을 것으로 계산된 소득. */
    private Long expectedIncome;
}
