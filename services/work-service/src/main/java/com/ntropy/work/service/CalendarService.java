package com.ntropy.work.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.common.dto.work.summary.CalendarDaySummary;
import com.ntropy.common.dto.work.summary.CalendarJobBrief;
import com.ntropy.common.dto.work.summary.CalendarMonthlyHours;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;
import com.ntropy.work.domain.entity.AllocationGoal;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.AllocationGoalMapper;
import com.ntropy.work.mapper.WorkLogMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final String DAY_SETTLEMENT_COMPLETED = "COMPLETED";
    private static final String DAY_SETTLEMENT_PENDING = "PENDING";

    private final WorkLogMapper workLogMapper;
    private final AllocationGoalMapper allocationGoalMapper;
    private final JobService jobService;

    public CalendarMonthlySummary getMonthlySummary(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        String targetMonth = yearMonth.toString(); // "2026-07"

        List<Job> jobs = jobService.findByUserId(userId);
        Map<Long, String> jobNames = jobs.stream()
                .collect(Collectors.toMap(Job::getJobId, Job::getJobName, (a, b) -> a));
        List<Long> jobIds = jobs.stream().map(Job::getJobId).collect(Collectors.toList());

        List<WorkLog> workLogs = workLogMapper.findByUserIdAndDateRange(userId, startDate, endDate);
        List<AllocationGoal> allocationGoals = jobIds.isEmpty()
                ? List.of()
                : allocationGoalMapper.findByJobIdsAndTargetMonth(jobIds, targetMonth);

        CalendarMonthlyHours hours = summarizeHours(workLogs, allocationGoals);
        List<CalendarDaySummary> days = summarizeDays(workLogs, jobNames);

        return new CalendarMonthlySummary(year, month, hours, days);
    }

    /**
     * plannedHours: 해당 월 ALLOCATION_GOAL(잡별 추천 근무시간) 합
     * actualHours: 해당 월 WORK_LOG 전체(PLANNED+CONFIRMED) 근무시간 합
     */
    private CalendarMonthlyHours summarizeHours(List<WorkLog> workLogs, List<AllocationGoal> allocationGoals) {
        int plannedHours = allocationGoals.stream()
                .mapToInt(goal -> goal.getRecommendHour() == null ? 0 : goal.getRecommendHour().intValue())
                .sum();

        int actualHours = 0;
        long expectedIncome = 0;
        for (WorkLog workLog : workLogs) {
            actualHours += durationHours(workLog.getStartTime(), workLog.getEndTime());
            if (workLog.getEstimatedIncome() != null) {
                expectedIncome += workLog.getEstimatedIncome();
            }
        }
        return new CalendarMonthlyHours(plannedHours, actualHours, expectedIncome);
    }

    private List<CalendarDaySummary> summarizeDays(List<WorkLog> workLogs, Map<Long, String> jobNames) {
        Map<LocalDate, List<WorkLog>> byDate = new TreeMap<>();
        for (WorkLog workLog : workLogs) {
            byDate.computeIfAbsent(workLog.getWorkDate(), d -> new ArrayList<>()).add(workLog);
        }

        List<CalendarDaySummary> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<WorkLog>> entry : byDate.entrySet()) {
            List<WorkLog> dayLogs = entry.getValue();

            boolean allCompleted = dayLogs.stream()
                    .allMatch(w -> SettlementStatus.COMPLETED.equals(w.getSettlementStatus()));
            String settlementStatus = allCompleted ? DAY_SETTLEMENT_COMPLETED : DAY_SETTLEMENT_PENDING;

            Map<Long, CalendarJobBrief> jobsById = new LinkedHashMap<>();
            for (WorkLog workLog : dayLogs) {
                jobsById.computeIfAbsent(workLog.getJobId(),
                        jobId -> new CalendarJobBrief(jobId, jobNames.get(jobId)));
            }

            days.add(new CalendarDaySummary(entry.getKey(), settlementStatus, new ArrayList<>(jobsById.values())));
        }
        return days;
    }

    private int durationHours(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return 0;
        }
        return (int) Duration.between(startTime, endTime).toHours();
    }
}
