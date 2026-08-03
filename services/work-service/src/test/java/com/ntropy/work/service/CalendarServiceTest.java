package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.dto.work.summary.CalendarDailySummary;
import com.ntropy.common.dto.work.summary.CalendarFatigueGauge;
import com.ntropy.common.dto.work.summary.CalendarMonthlySummary;
import com.ntropy.work.domain.entity.AllocationGoal;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryAllocationGoalMapper;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobScheduleMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;

class CalendarServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long JOB_A = 10L;
    private static final Long JOB_B = 20L;
    private static final String TARGET_MONTH = "2026-08";

    private InMemoryWorkLogMapper workLogMapper;
    private InMemoryAllocationGoalMapper allocationGoalMapper;
    private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        InMemoryJobMapper jobMapper = new InMemoryJobMapper();
        jobMapper.seed(job(JOB_A, "배민 배달"));
        jobMapper.seed(job(JOB_B, "쿠팡이츠 배달"));

        workLogMapper = new InMemoryWorkLogMapper();
        allocationGoalMapper = new InMemoryAllocationGoalMapper();
        JobService jobService = new JobService(
                jobMapper, new InMemoryJobScheduleMapper(), new CategoryService(new InMemoryCategoryMapper())
        );

        calendarService = new CalendarService(
                workLogMapper, allocationGoalMapper, jobService, new StubFatigueService(null)
        );
    }

    private Job job(Long jobId, String jobName) {
        Job job = Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName(jobName)
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(12000)
                .isRegular(false)
                .baseFatigue(3)
                .isActive(true)
                .build();
        job.setJobId(jobId);
        return job;
    }

    private void seedWorkLog(Long jobId, LocalDate date, LocalTime start, LocalTime end,
                              String status, SettlementStatus settlementStatus, Long estimatedIncome) {
        workLogMapper.insert(WorkLog.builder()
                .userId(USER_ID)
                .jobId(jobId)
                .workDate(date)
                .startTime(start)
                .endTime(end)
                .status(status)
                .settlementStatus(settlementStatus)
                .estimatedIncome(estimatedIncome)
                .build());
    }

    @Test
    @DisplayName("월간 요약은 목표시간 합/근무시간 합/예상수입 합을 계산한다")
    void monthlySummary_aggregatesPlannedActualAndIncome() {
        allocationGoalMapper.insert(AllocationGoal.builder().jobId(JOB_A).targetMonth(TARGET_MONTH).recommendHour(20L).build());
        allocationGoalMapper.insert(AllocationGoal.builder().jobId(JOB_B).targetMonth(TARGET_MONTH).recommendHour(10L).build());
        seedWorkLog(JOB_A, LocalDate.of(2026, 8, 3), LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);
        seedWorkLog(JOB_B, LocalDate.of(2026, 8, 5), LocalTime.of(10, 0), LocalTime.of(14, 0),
                "CONFIRMED", SettlementStatus.PENDING, 20000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8);

        assertEquals(30, summary.getSummary().getPlannedHours());
        assertEquals(8, summary.getSummary().getActualHours());
        assertEquals(68000L, summary.getSummary().getExpectedIncome());
    }

    @Test
    @DisplayName("월 범위 밖의 근무일지는 집계에서 제외된다")
    void monthlySummary_excludesLogsOutsideDateRange() {
        seedWorkLog(JOB_A, LocalDate.of(2026, 7, 31), LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8);

        assertEquals(0, summary.getSummary().getActualHours());
        assertTrue(summary.getDays().isEmpty());
    }

    @Test
    @DisplayName("하루의 근무일지가 전부 COMPLETED여야 그날이 COMPLETED로 표시된다")
    void monthlySummary_dayIsCompletedOnlyWhenAllLogsCompleted() {
        LocalDate mixedDay = LocalDate.of(2026, 8, 3);
        seedWorkLog(JOB_A, mixedDay, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);
        seedWorkLog(JOB_B, mixedDay, LocalTime.of(14, 0), LocalTime.of(16, 0),
                "CONFIRMED", SettlementStatus.PENDING, 8000L);

        LocalDate completedDay = LocalDate.of(2026, 8, 10);
        seedWorkLog(JOB_A, completedDay, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8);

        assertEquals("PENDING", findDay(summary, mixedDay).getSettlementStatus());
        assertEquals("COMPLETED", findDay(summary, completedDay).getSettlementStatus());
    }

    @Test
    @DisplayName("같은 날 같은 잡의 근무일지가 여러 개여도 잡 목록은 중복 없이 1개다")
    void monthlySummary_jobBriefsAreDeduplicatedPerDay() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        seedWorkLog(JOB_A, date, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 36000L);
        seedWorkLog(JOB_A, date, LocalTime.of(14, 0), LocalTime.of(16, 0),
                "CONFIRMED", SettlementStatus.COMPLETED, 24000L);

        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8);

        assertEquals(1, findDay(summary, date).getJobs().size());
    }

    @Test
    @DisplayName("근무 이력이 없으면 시간 합계는 0이고 일자 목록은 비어 있다")
    void monthlySummary_noDataReturnsZeroedSummary() {
        CalendarMonthlySummary summary = calendarService.getMonthlySummary(USER_ID, 2026, 8);

        assertEquals(0, summary.getSummary().getPlannedHours());
        assertEquals(0, summary.getSummary().getActualHours());
        assertEquals(0L, summary.getSummary().getExpectedIncome());
        assertTrue(summary.getDays().isEmpty());
    }

    @Test
    @DisplayName("일간 요약은 근무 목록과 요일 한글 표기를 채운다")
    void dailySummary_returnsWorksAndKoreanDayOfWeek() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        seedWorkLog(JOB_A, monday, LocalTime.of(18, 0), LocalTime.of(22, 0),
                "PLANNED", SettlementStatus.NONE, 48000L);

        CalendarDailySummary summary = calendarService.getDailySummary(USER_ID, monday);

        assertEquals("월", summary.getDayOfWeek());
        assertEquals(1, summary.getWorks().size());
        assertEquals("배민 배달", summary.getWorks().get(0).getJobName());
    }

    @Test
    @DisplayName("일간 요약의 피로도 게이지는 FatigueService 결과를 그대로 담는다")
    void dailySummary_attachesFatigueGaugeFromFatigueService() {
        CalendarFatigueGauge gauge = new CalendarFatigueGauge(42, "LOW", false);
        JobService jobService = new JobService(
                new InMemoryJobMapper(), new InMemoryJobScheduleMapper(), new CategoryService(new InMemoryCategoryMapper())
        );
        CalendarService service = new CalendarService(
                workLogMapper, allocationGoalMapper, jobService, new StubFatigueService(gauge)
        );

        CalendarDailySummary summary = service.getDailySummary(USER_ID, LocalDate.of(2026, 8, 3));

        assertEquals(gauge, summary.getFatigue());
    }

    private com.ntropy.common.dto.work.summary.CalendarDaySummary findDay(CalendarMonthlySummary summary, LocalDate date) {
        return summary.getDays().stream()
                .filter(day -> day.getDate().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("해당 날짜의 요약이 없습니다: " + date));
    }

    private static class StubFatigueService extends FatigueService {

        private final CalendarFatigueGauge gauge;

        StubFatigueService(CalendarFatigueGauge gauge) {
            super(null);
            this.gauge = gauge;
        }

        @Override
        public CalendarFatigueGauge calculateGauge(Long userId, LocalDate date) {
            return gauge;
        }
    }
}
