package com.ntropy.common.dto.work.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * plannedHours: 해당 월 ALLOCATION_GOAL(잡별 추천 근무시간) 합
 * actualHours: 해당 월 WORK_LOG 전체(PLANNED+CONFIRMED) 근무시간 합
 * expectedIncome: 전체 WORK_LOG의 estimated_income 합
 * targetAmount: 해당 월 SAVING_GOAL.target_amount. 저축 목표를 등록하지 않은 달은 null
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarMonthlyHours {

    private int plannedHours;
    private int actualHours;
    private Long expectedIncome;
    private Long targetAmount;
}
