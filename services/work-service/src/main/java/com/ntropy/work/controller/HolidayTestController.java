package com.ntropy.work.controller;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.work.service.HolidayService;

import lombok.RequiredArgsConstructor;

/**
 * [테스트용] 특일 정보 API 연동(HolidayService)이 실제로 동작하는지 눈으로 확인하기 위한
 * 임시 컨트롤러. BFF 패턴(REST 컨트롤러는 bff-service에만 위치) 원칙을 벗어나 work-service에
 * 직접 뒀다 - 정식 기능이 아니라 검증 끝나면 지울 것.
 */
@RestController
@RequestMapping("/internal/test/holidays")
@RequiredArgsConstructor
public class HolidayTestController {

    private final HolidayService holidayService;

    @GetMapping
    public Set<LocalDate> getHolidays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return new TreeSet<>(holidayService.getHolidays(startDate, endDate));
    }
}
