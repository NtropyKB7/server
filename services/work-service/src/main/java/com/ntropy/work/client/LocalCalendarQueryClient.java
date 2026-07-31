package com.ntropy.work.client;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;
import com.ntropy.work.service.CalendarService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalCalendarQueryClient implements CalendarQueryClient {

    private final CalendarService calendarService;

    @Override
    public CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month) {
        return calendarService.getMonthlySummary(userId, year, month);
    }
}
