package com.ntropy.bff.controller.work;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarQueryClient calendarQueryClient;

    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlySummary> getMonthlySummary(@RequestParam Long userId,
                                                                   @RequestParam int year,
                                                                   @RequestParam int month,
                                                                   @RequestParam(required = false) Double latitude,
                                                                   @RequestParam(required = false) Double longitude) {
        return ApiResponse.success(calendarQueryClient.getMonthlySummary(userId, year, month, latitude, longitude));
    }

    @GetMapping("/daily")
    public ApiResponse<CalendarDailySummary> getDailySummary(@RequestParam Long userId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(required = false) Double latitude,
                                                               @RequestParam(required = false) Double longitude) {
        return ApiResponse.success(calendarQueryClient.getDailySummary(userId, date, latitude, longitude));
    }
}
