package com.ntropy.notification.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;

class StubCalendarQueryClient implements CalendarQueryClient {

    private final Map<String, CalendarDailySummary> dailySummaries = new HashMap<>();

    StubCalendarQueryClient withDailySummary(Long userId, LocalDate date, CalendarDailySummary summary) {
        dailySummaries.put(key(userId, date), summary);
        return this;
    }

    @Override
    public CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month, Double latitude, Double longitude) {
        throw new UnsupportedOperationException("이 테스트에서는 사용하지 않음");
    }

    @Override
    public CalendarDailySummary getDailySummary(Long userId, LocalDate date, Double latitude, Double longitude) {
        CalendarDailySummary summary = dailySummaries.get(key(userId, date));
        if (summary == null) {
            return new CalendarDailySummary(date, null, java.util.List.of(), null, null);
        }
        return summary;
    }

    private String key(Long userId, LocalDate date) {
        return userId + "-" + date;
    }
}
