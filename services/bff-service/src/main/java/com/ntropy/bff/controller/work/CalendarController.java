package com.ntropy.bff.controller.work;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.security.AuthenticatedUserIdResolver;
import com.ntropy.common.client.CalendarQueryClient;
import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;

import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarQueryClient calendarQueryClient;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlySummary> getMonthlySummary(@ApiParam(hidden = true) Authentication authentication,
                                                                   @RequestParam int year,
                                                                   @RequestParam int month,
                                                                   @RequestParam(required = false) Double latitude,
                                                                   @RequestParam(required = false) Double longitude) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(calendarQueryClient.getMonthlySummary(userId, year, month, latitude, longitude));
    }

    @GetMapping("/daily")
    public ApiResponse<CalendarDailySummary> getDailySummary(@ApiParam(hidden = true) Authentication authentication,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(required = false) Double latitude,
                                                               @RequestParam(required = false) Double longitude) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(calendarQueryClient.getDailySummary(userId, date, latitude, longitude));
    }
}
