package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.WorkLog;
import com.ntropy.work.domain.enums.SettlementStatus;
import com.ntropy.work.mapper.InMemorySavingGoalMapper;
import com.ntropy.work.mapper.InMemoryWorkLogMapper;

/**
 * FatigueService.calculateGauge() 개선 전(하루씩 7번 조회)과 개선 후(범위 1번 벌크 조회)의
 * 호출 횟수를 비교하는 테스트. 실제 DB 대신 InMemory Fake(다른 테스트에서 쓰는 것과 동일)를
 * 상속해 메서드 호출 횟수만 세는 방식이라 별도 인프라 없이 항상 빠르게 돌아간다.
 *
 * <p>개선 전 방식은 프로덕션 코드에서 이미 제거됐으므로, 옛 호출 패턴(findByUserIdAndWorkDate를
 * 7번 호출)을 이 테스트 안에서 그대로 재현해 비교 기준선으로 삼는다.</p>
 *
 * <p>이 최적화는 요청(캘린더 일간 조회) 1건당 호출되는 로직이라, 요청 수(N)가 늘어도 개선 전/후
 * 둘 다 호출 횟수는 N에 비례해서 늘어난다. 다만 요청 1건당 호출 횟수가 8회 -> 2회로 줄어드는
 * 것이므로, 총 호출 수는 N과 무관하게 항상 4배 차이가 난다.</p>
 */
class FatigueGaugeQueryCountComparisonTest {

    private static final Long USER_ID_BASE = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 10);
    private static final YearMonth TARGET_MONTH = YearMonth.from(TARGET_DATE);

    /** 개선 전 FatigueService가 실제로 실행하던 호출 수: 하루씩 WINDOW_DAYS(7)번 + SAVING_GOAL 조회 1번. */
    private static final int OLD_STYLE_CALL_COUNT_PER_REQUEST = FatigueService.WINDOW_DAYS + 1;
    /** 개선 후: 범위 조회 1번 + SAVING_GOAL 조회 1번. */
    private static final int NEW_STYLE_CALL_COUNT_PER_REQUEST = 2;

    @Test
    @DisplayName("요청 300건 기준, 범위 벌크 조회는 하루씩 루프 조회보다 매퍼 호출 횟수가 4배 적다")
    void bulkRangeQueryUsesFewerCallsThanOldPerDayLoop() {
        int requestCount = 300;

        CallCountingWorkLogMapper workLogMapper = new CallCountingWorkLogMapper();
        CallCountingSavingGoalMapper savingGoalMapper = new CallCountingSavingGoalMapper();
        List<Long> userIds = seedWorkLogs(workLogMapper, requestCount);
        FatigueService fatigueService = new FatigueService(savingGoalMapper);

        workLogMapper.reset();
        savingGoalMapper.reset();
        for (Long userId : userIds) {
            oldStyleFatigueQueries(workLogMapper, savingGoalMapper, userId);
        }
        int oldCallCount = workLogMapper.getCallCount() + savingGoalMapper.getCallCount();

        workLogMapper.reset();
        savingGoalMapper.reset();
        for (Long userId : userIds) {
            List<WorkLog> workLogs = workLogMapper.findByUserIdAndDateRange(
                    userId, TARGET_DATE.minusDays(FatigueService.WINDOW_DAYS - 1L), TARGET_DATE);
            fatigueService.calculateGauge(userId, TARGET_DATE, workLogs);
        }
        int newCallCount = workLogMapper.getCallCount() + savingGoalMapper.getCallCount();

        System.out.println("===== FatigueService 게이지 계산 매퍼 호출 수 비교 (요청 " + requestCount + "건) =====");
        System.out.println("개선 전(하루씩 루프) : 호출 " + oldCallCount + "회");
        System.out.println("개선 후(범위 벌크)   : 호출 " + newCallCount + "회");
        System.out.println("=======================================================");

        assertEquals(OLD_STYLE_CALL_COUNT_PER_REQUEST * requestCount, oldCallCount,
                "개선 전 방식의 호출 수가 예상(요청당 " + OLD_STYLE_CALL_COUNT_PER_REQUEST + "회)과 다릅니다");
        assertEquals(NEW_STYLE_CALL_COUNT_PER_REQUEST * requestCount, newCallCount,
                "개선 후 방식의 호출 수가 예상(요청당 " + NEW_STYLE_CALL_COUNT_PER_REQUEST + "회)과 다릅니다");
        assertTrue(newCallCount < oldCallCount, "개선 후 방식이 개선 전보다 호출 수가 적어야 합니다");
    }

    /** 개선 전 FatigueService.calculateGauge()가 실행하던 호출 패턴을 그대로 재현한다. */
    private void oldStyleFatigueQueries(InMemoryWorkLogMapper workLogMapper, InMemorySavingGoalMapper savingGoalMapper,
                                         Long userId) {
        for (int n = 0; n < FatigueService.WINDOW_DAYS; n++) {
            workLogMapper.findByUserIdAndWorkDate(userId, TARGET_DATE.minusDays(n));
        }
        savingGoalMapper.findByUserIdAndTargetMonth(userId, TARGET_MONTH.toString());
    }

    /** requestCount명의 사용자를 만들고 TARGET_DATE에 근무일지 1건씩 심어준다. */
    private List<Long> seedWorkLogs(InMemoryWorkLogMapper workLogMapper, int requestCount) {
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            long userId = USER_ID_BASE + i;
            userIds.add(userId);
            workLogMapper.insert(WorkLog.builder()
                    .userId(userId)
                    .jobId(1L)
                    .workDate(TARGET_DATE)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .status("CONFIRMED")
                    .settlementStatus(SettlementStatus.PENDING)
                    .fatigue(3L)
                    .build());
        }
        return userIds;
    }

    /** InMemoryWorkLogMapper를 그대로 쓰되, 실제 계산에 관여하는 두 조회 메서드의 호출 횟수만 센다. */
    private static class CallCountingWorkLogMapper extends InMemoryWorkLogMapper {
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public List<WorkLog> findByUserIdAndWorkDate(Long userId, LocalDate workDate) {
            callCount.incrementAndGet();
            return super.findByUserIdAndWorkDate(userId, workDate);
        }

        @Override
        public List<WorkLog> findByUserIdAndDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
            callCount.incrementAndGet();
            return super.findByUserIdAndDateRange(userId, startDate, endDate);
        }

        int getCallCount() {
            return callCount.get();
        }

        void reset() {
            callCount.set(0);
        }
    }

    /** InMemorySavingGoalMapper를 그대로 쓰되, resolveTargetFatigue가 부르는 조회 메서드의 호출 횟수만 센다. */
    private static class CallCountingSavingGoalMapper extends InMemorySavingGoalMapper {
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public com.ntropy.work.domain.entity.SavingGoal findByUserIdAndTargetMonth(Long userId, String targetMonth) {
            callCount.incrementAndGet();
            return super.findByUserIdAndTargetMonth(userId, targetMonth);
        }

        int getCallCount() {
            return callCount.get();
        }

        void reset() {
            callCount.set(0);
        }
    }
}
