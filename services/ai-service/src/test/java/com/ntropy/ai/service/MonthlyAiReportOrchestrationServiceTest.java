package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class MonthlyAiReportOrchestrationServiceTest {

    @Test
    void runBatch_whenTargetUserListIsEmpty_completesWithoutException() {
        MonthlyAiReportOrchestrationService orchestrationService =
                new MonthlyAiReportOrchestrationService(
                        null,
                        null,
                        () -> List.of()
                );

        YearMonth targetYearMonth =
                YearMonth.of(2026, 7);

        assertDoesNotThrow(
                () -> orchestrationService.runBatch(targetYearMonth)
        );
    }
}