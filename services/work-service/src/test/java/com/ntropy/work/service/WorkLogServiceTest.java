package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.IncomingTransactionQueryClient;
import com.ntropy.common.dto.account.internal.NormalizedIncomingTransaction;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.client.holiday.HolidayApiClient;
import com.ntropy.work.domain.entity.Job;
import com.ntropy.work.domain.entity.JobPlatformMapping;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.domain.enums.SettlementType;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryHolidayMapper;
import com.ntropy.work.mapper.InMemoryJobMapper;
import com.ntropy.work.mapper.InMemoryJobPlatformMappingMapper;
import com.ntropy.work.mapper.InMemoryJobScheduleMapper;
import com.ntropy.work.mapper.InMemoryPlatformMapper;
import com.ntropy.work.mapper.InMemorySettlementMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;

class WorkLogServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 3);
    private static final Long ON_DEMAND_PLATFORM_ID = 900L;

    private InMemoryJobMapper jobMapper;
    private InMemoryWorkLogMapper workLogMapper;
    private InMemoryJobPlatformMappingMapper jobPlatformMappingMapper;
    private InMemorySettlementMapper settlementMapper;
    private WorkLogService workLogService;

    @BeforeEach
    void setUp() {
        jobMapper = new InMemoryJobMapper();
        workLogMapper = new InMemoryWorkLogMapper();
        jobPlatformMappingMapper = new InMemoryJobPlatformMappingMapper();
        settlementMapper = new InMemorySettlementMapper();
        InMemoryPlatformMapper platformMapper = new InMemoryPlatformMapper();
        platformMapper.seed(Platform.builder()
                .platformId(ON_DEMAND_PLATFORM_ID)
                .depositName("카카오모빌리티")
                .settlementCycle("DAILY")
                .settlementTriggerType("ON_DEMAND")
                .build());
        JobService jobService = new JobService(
                jobMapper, new InMemoryJobScheduleMapper(), new CategoryService(new InMemoryCategoryMapper())
        );
        HolidayService holidayService =
                new HolidayService(new HolidayApiClient(null, null), new InMemoryHolidayMapper());
        ActiveUserQueryClient activeUserQueryClient = List::of;
        SettlementService settlementService = new SettlementService(
                new StubIncomingTransactionQueryClient(), activeUserQueryClient, platformMapper, jobMapper,
                jobPlatformMappingMapper, workLogMapper, settlementMapper, holidayService);
        workLogService = new WorkLogService(workLogMapper, jobService, settlementService);
    }

    private static final class StubIncomingTransactionQueryClient implements IncomingTransactionQueryClient {
        @Override
        public List<NormalizedIncomingTransaction> findIncomingTransactions(
                Long userId, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }
    }

    private Job hourlyJob() {
        Job job = Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("배민 배달")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(12000)
                .isRegular(false)
                .baseFatigue(3)
                .isActive(true)
                .build();
        jobMapper.seed(setJobId(job, 100L));
        return job;
    }

    private Job perTaskJob() {
        Job job = Job.builder()
                .userId(USER_ID)
                .categoryId(1L)
                .jobName("쿠팡이츠 배달")
                .settlementType(SettlementType.PER_TASK)
                .perTaskWage(3000)
                .taskPerHour(2.0f)
                .isRegular(false)
                .baseFatigue(4)
                .isActive(true)
                .build();
        jobMapper.seed(setJobId(job, 200L));
        return job;
    }

    private Job onDemandJob() {
        Job job = Job.builder()
                .userId(USER_ID)
                .categoryId(2L)
                .jobName("카카오T대리")
                .settlementType(SettlementType.HOURLY)
                .hourlyWage(10000)
                .isRegular(false)
                .baseFatigue(5)
                .isActive(true)
                .build();
        jobMapper.seed(setJobId(job, 300L));
        jobPlatformMappingMapper.insert(
                JobPlatformMapping.builder().jobId(job.getJobId()).platformId(ON_DEMAND_PLATFORM_ID).build());
        return job;
    }

    private Job setJobId(Job job, Long jobId) {
        job.setJobId(jobId);
        return job;
    }

    private WorkLog planOf(Long jobId, LocalTime start, LocalTime end) {
        return WorkLog.builder()
                .userId(USER_ID)
                .jobId(jobId)
                .workDate(WORK_DATE)
                .startTime(start)
                .endTime(end)
                .build();
    }

    @Test
    @DisplayName("계획 등록 시 fatigue 기본값과 예상수입이 채워진다")
    void registerPlan_fillsDefaultsAndCalculatesEstimatedIncome() {
        Job job = hourlyJob();
        WorkLog plan = planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0));

        WorkLog result = workLogService.registerPlan(plan);

        assertEquals("PLANNED", result.getStatus());
        assertEquals(SettlementStatus.NONE, result.getSettlementStatus());
        assertEquals(job.getBaseFatigue().longValue(), result.getFatigue());
        assertNull(result.getTaskCount());
        assertEquals(48000L, result.getEstimatedIncome());
    }

    @Test
    @DisplayName("계획 등록 시 fatigue를 입력하면 그대로 유지된다")
    void registerPlan_keepsProvidedFatigue() {
        Job job = hourlyJob();
        WorkLog plan = planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0));
        plan.setFatigue(5L);

        WorkLog result = workLogService.registerPlan(plan);

        assertEquals(5L, result.getFatigue());
    }

    @Test
    @DisplayName("필수값이 없으면 계획 등록이 실패한다")
    void registerPlan_missingRequiredField_throws() {
        WorkLog plan = planOf(null, LocalTime.of(18, 0), LocalTime.of(22, 0));

        assertThrows(ServiceException.class, () -> workLogService.registerPlan(plan));
    }

    @Test
    @DisplayName("겹치는 시간대에 계획을 등록하면 실패한다")
    void registerPlan_overlappingTime_throws() {
        Job job = hourlyJob();
        workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        WorkLog overlapping = planOf(job.getJobId(), LocalTime.of(20, 0), LocalTime.of(23, 0));

        assertThrows(ServiceException.class, () -> workLogService.registerPlan(overlapping));
    }

    @Test
    @DisplayName("겹침 검증은 같은 유저의 다른 잡까지 포함한다")
    void registerPlan_overlapCheckIsAcrossJobsForSameUser() {
        Job jobA = hourlyJob();
        Job jobB = perTaskJob();
        workLogService.registerPlan(planOf(jobA.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        WorkLog overlapping = planOf(jobB.getJobId(), LocalTime.of(19, 0), LocalTime.of(21, 0));

        assertThrows(ServiceException.class, () -> workLogService.registerPlan(overlapping));
    }

    @Test
    @DisplayName("실적 등록은 fatigue가 없으면 실패한다")
    void registerActual_requiresFatigue() {
        Job job = hourlyJob();
        WorkLog actual = planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0));

        assertThrows(ServiceException.class, () -> workLogService.registerActual(actual));
    }

    @Test
    @DisplayName("건별 정산 잡은 taskCount 없이 실적 등록하면 실패한다")
    void registerActual_perTaskWithoutTaskCount_throws() {
        Job job = perTaskJob();
        WorkLog actual = planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0));
        actual.setFatigue(4L);

        assertThrows(ServiceException.class, () -> workLogService.registerActual(actual));
    }

    @Test
    @DisplayName("건별 정산 잡은 taskCount 기준으로 예상수입을 계산한다")
    void registerActual_perTaskWithTaskCount_calculatesByTaskCount() {
        Job job = perTaskJob();
        WorkLog actual = planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0));
        actual.setFatigue(4L);
        actual.setTaskCount(5L);

        WorkLog result = workLogService.registerActual(actual);

        assertEquals("CONFIRMED", result.getStatus());
        assertEquals(SettlementStatus.PENDING, result.getSettlementStatus());
        assertEquals(15000L, result.getEstimatedIncome());
    }

    @Test
    @DisplayName("자정을 넘는 근무도 예상수입이 정확히 계산된다")
    void registerActual_overnightWork_calculatesAcrossMidnight() {
        Job job = hourlyJob();
        WorkLog actual = planOf(job.getJobId(), LocalTime.of(23, 0), LocalTime.of(1, 0));
        actual.setFatigue(4L);

        WorkLog result = workLogService.registerActual(actual);

        assertEquals(24000L, result.getEstimatedIncome());
    }

    @Test
    @DisplayName("수정 시 전달한 필드만 반영되고 나머지는 유지된다")
    void editWorkLog_onlyPatchedFieldsChange() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        WorkLog patch = WorkLog.builder().endTime(LocalTime.of(23, 0)).build();
        WorkLog result = workLogService.editWorkLog(USER_ID, plan.getLogId(), patch);

        assertEquals(LocalTime.of(18, 0), result.getStartTime());
        assertEquals(LocalTime.of(23, 0), result.getEndTime());
        assertEquals(60000L, result.getEstimatedIncome());
    }

    @Test
    @DisplayName("수정 시 겹침 검증에서 자기 자신은 제외된다")
    void editWorkLog_overlapCheckExcludesSelf() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        WorkLog patch = WorkLog.builder().startTime(LocalTime.of(18, 30)).build();

        assertEquals(LocalTime.of(18, 30),
                workLogService.editWorkLog(USER_ID, plan.getLogId(), patch).getStartTime());
    }

    @Test
    @DisplayName("존재하지 않는 근무일지를 수정하면 실패한다")
    void editWorkLog_notFound_throws() {
        WorkLog patch = WorkLog.builder().endTime(LocalTime.of(23, 0)).build();

        assertThrows(ServiceException.class, () -> workLogService.editWorkLog(USER_ID, 999L, patch));
    }

    @Test
    @DisplayName("다른 유저의 근무일지를 수정하면 실패한다")
    void editWorkLog_notOwner_throws() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));
        WorkLog patch = WorkLog.builder().endTime(LocalTime.of(23, 0)).build();

        assertThrows(ServiceException.class, () -> workLogService.editWorkLog(999L, plan.getLogId(), patch));
    }

    @Test
    @DisplayName("확정하면 PLANNED에서 CONFIRMED로 전환된다")
    void confirmWorkLog_transitionsPlannedToConfirmed() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        WorkLog result = workLogService.confirmWorkLog(USER_ID, plan.getLogId(), WorkLog.builder().build());

        assertEquals("CONFIRMED", result.getStatus());
        assertEquals(SettlementStatus.PENDING, result.getSettlementStatus());
    }

    @Test
    @DisplayName("이미 확정된 근무일지를 다시 확정하면 실패한다")
    void confirmWorkLog_alreadyConfirmed_throws() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));
        workLogService.confirmWorkLog(USER_ID, plan.getLogId(), WorkLog.builder().build());

        assertThrows(ServiceException.class,
                () -> workLogService.confirmWorkLog(USER_ID, plan.getLogId(), WorkLog.builder().build()));
    }

    @Test
    @DisplayName("건별 정산 잡은 taskCount 없이 확정하면 실패한다")
    void confirmWorkLog_perTaskWithoutTaskCount_throws() {
        Job job = perTaskJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        assertThrows(ServiceException.class,
                () -> workLogService.confirmWorkLog(USER_ID, plan.getLogId(), WorkLog.builder().build()));
    }

    @Test
    @DisplayName("다른 유저의 근무일지를 확정하면 실패한다")
    void confirmWorkLog_notOwner_throws() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        assertThrows(ServiceException.class,
                () -> workLogService.confirmWorkLog(999L, plan.getLogId(), WorkLog.builder().build()));
    }

    @Test
    @DisplayName("삭제하면 근무일지가 조회되지 않는다")
    void deleteWorkLog_removesRecord() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        workLogService.deleteWorkLog(USER_ID, plan.getLogId());

        assertThrows(ServiceException.class, () -> workLogService.findById(plan.getLogId()));
    }

    @Test
    @DisplayName("ON_DEMAND 플랫폼 잡은 실적 등록 시 PENDING을 거치지 않고 즉시 정산 완료 처리되지만 SETTLEMENT는 생성하지 않는다")
    void registerActual_onDemandPlatform_completesImmediatelyWithoutCreatingSettlement() {
        Job job = onDemandJob();
        WorkLog actual = planOf(job.getJobId(), LocalTime.of(20, 0), LocalTime.of(22, 0));
        actual.setFatigue(4L);

        WorkLog result = workLogService.registerActual(actual);

        assertEquals(SettlementStatus.COMPLETED, result.getSettlementStatus());
        assertEquals(0, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("자동 정산 플랫폼 잡은 확정해도 SETTLEMENT가 즉시 생성되지 않는다")
    void confirmWorkLog_autoPlatform_doesNotCreateSettlementImmediately() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        workLogService.confirmWorkLog(USER_ID, plan.getLogId(), WorkLog.builder().build());

        assertEquals(0, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("ON_DEMAND 플랫폼 잡은 확정 시 즉시 정산 완료 처리되지만 SETTLEMENT는 나중에 실제 출금 거래로만 생긴다")
    void confirmWorkLog_onDemandPlatform_completesImmediatelyWithoutCreatingSettlement() {
        Job job = onDemandJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(20, 0), LocalTime.of(22, 0)));

        WorkLog result = workLogService.confirmWorkLog(USER_ID, plan.getLogId(), WorkLog.builder().build());

        assertEquals("CONFIRMED", result.getStatus());
        assertEquals(SettlementStatus.COMPLETED, result.getSettlementStatus());
        assertEquals(0, settlementMapper.findAll().size());
    }

    @Test
    @DisplayName("존재하지 않는 근무일지를 삭제하면 실패한다")
    void deleteWorkLog_notFound_throws() {
        assertThrows(ServiceException.class, () -> workLogService.deleteWorkLog(USER_ID, 999L));
    }

    @Test
    @DisplayName("다른 유저의 근무일지를 삭제하면 실패한다")
    void deleteWorkLog_notOwner_throws() {
        Job job = hourlyJob();
        WorkLog plan = workLogService.registerPlan(planOf(job.getJobId(), LocalTime.of(18, 0), LocalTime.of(22, 0)));

        assertThrows(ServiceException.class, () -> workLogService.deleteWorkLog(999L, plan.getLogId()));
    }
}
