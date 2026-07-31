package com.ntropy.common.client;

import java.time.LocalDate;

import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;

/**
 * work-service의 캘린더(WORK_LOG 집계) 조회 계약. work-service가 LocalCalendarQueryClient로
 * 구현하고, bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface CalendarQueryClient {

    CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month);

    CalendarDailySummary getDailySummary(Long userId, LocalDate date);
}
