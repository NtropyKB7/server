package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

/**
 * 월간 AI 리포트 오케스트레이터의 기본 동작을 검증하는 테스트입니다.
 *
 * 아직 배치 대상 사용자 조회 Client가 연결되지 않았으므로,
 * 빈 사용자 목록을 처리할 때 예외 없이 정상 종료되는지를 확인합니다.
 */
class MonthlyAiReportOrchestrationServiceTest {

    /**
     * 대상 사용자가 없는 경우에도 배치 전체가 실패하지 않아야 합니다.
     */
    @Test
    void runBatch_whenTargetUserListIsEmpty_completesWithoutException() {
        // 현재 오케스트레이터는 외부 Client 의존성이 없는 뼈대 상태입니다.
        MonthlyAiReportOrchestrationService orchestrationService =
                new MonthlyAiReportOrchestrationService(null, null);

        // 테스트에서 원하는 리포트 대상 연월을 직접 전달합니다.
        YearMonth targetYearMonth = YearMonth.of(2026, 7);

        // 대상 사용자가 0명이어도 예외 없이 완료되어야 합니다.
        assertDoesNotThrow(
                () -> orchestrationService.runBatch(targetYearMonth)
        );
    }
}