package com.ntropy.common.dto.work.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * plannedHours: status=PLANNED인 WORK_LOG 근무시간 합
 * actualHours: status=CONFIRMED인 WORK_LOG 근무시간 합
 * expectedIncome: 전체 WORK_LOG의 estimated_income 합
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthlyHours {

    private int plannedHours;
    private int actualHours;
    private Long expectedIncome;
}
