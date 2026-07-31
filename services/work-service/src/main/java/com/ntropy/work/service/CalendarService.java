package com.ntropy.work.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarDaySummary;
import com.ntropy.common.dto.work.summary.CalendarFatigueGauge;
import com.ntropy.common.dto.work.summary.CalendarJobBrief;
import com.ntropy.common.dto.work.summary.CalendarMonthlyHours;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;
import com.ntropy.common.dto.work.summary.CalendarWorkBrief;
import com.ntropy.work.domain.entity.AllocationGoal;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.AllocationGoalMapper;
import com.ntropy.work.mapper.WorkLogMapper;
import com.ntropy.work.util.WorkTimeUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final String DAY_SETTLEMENT_COMPLETED = "COMPLETED";
    private static final String DAY_SETTLEMENT_PENDING = "PENDING";

    private static final Map<DayOfWeek, String> KOREAN_DAY_OF_WEEK = new EnumMap<>(DayOfWeek.class);
    static {
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.MONDAY, "월");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.TUESDAY, "화");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.WEDNESDAY, "수");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.THURSDAY, "목");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.FRIDAY, "금");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.SATURDAY, "토");
        KOREAN_DAY_OF_WEEK.put(DayOfWeek.SUNDAY, "일");
    }

    private final WorkLogMapper workLogMapper;
    private final AllocationGoalMapper allocationGoalMapper;
    private final JobService jobService;
    private final FatigueService fatigueService;

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
     * fatigue는 7일 가중 게이지로 계산한다.
     */
    public CalendarDailySummary getDailySummary(Long userId, LocalDate date) {
        List<WorkLog> workLogs = workLogMapper.findByUserIdAndWorkDate(userId, date);

        List<Long> jobIds = workLogs.stream().map(WorkLog::getJobId).distinct().collect(Collectors.toList());
        Map<Long, String> jobNames = jobIds.stream()
                .collect(Collectors.toMap(jobId -> jobId, jobId -> jobService.findById(jobId).getJobName()));

        List<CalendarWorkBrief> works = workLogs.stream()
                .map(w -> new CalendarWorkBrief(w.getLogId(), w.getJobId(), jobNames.get(w.getJobId()),
                        w.getStartTime(), w.getEndTime(), w.getStatus()))
                .collect(Collectors.toList());

        String dayOfWeek = KOREAN_DAY_OF_WEEK.get(date.getDayOfWeek());
        CalendarFatigueGauge fatigue = fatigueService.calculateGauge(userId, date);

        return new CalendarDailySummary(date, dayOfWeek, works, fatigue);
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
            actualHours += WorkTimeUtils.durationHours(workLog.getStartTime(), workLog.getEndTime());
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
}
