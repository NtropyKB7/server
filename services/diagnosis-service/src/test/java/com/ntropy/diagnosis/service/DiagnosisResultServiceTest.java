package com.ntropy.diagnosis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.dto.DiagnosisCalculationInput;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;

/**
 * DiagnosisResultService의 계산 규칙을 검증하는 단위 테스트입니다.
 */
class DiagnosisResultServiceTest {

    /**
     * 총소득·총소비·고정지출을 기준으로
     * 순현금흐름과 고정지출 비율이 계산되는지 확인합니다.
     */
    @Test
    void calculateAndUpsert_calculatesDiagnosisValues() {
        InMemoryDiagnosisResultMapper mapper =
                new InMemoryDiagnosisResultMapper();

        DiagnosisResultService service =
                new DiagnosisResultService(mapper);

        DiagnosisCalculationInput input =
                new DiagnosisCalculationInput(
                        1L,
                        "2026-07",
                        3_000_000L,
                        100_000L,
                        2_000_000L,
                        600_000L,
                        5_000_000L,
                        3_000_000L,
                        2_000_000L
                );

        DiagnosisResult result =
                service.calculateAndUpsert(input);

        // netCashFlow = 3,000,000 - 2,000,000
        assertEquals(1_000_000L, result.getNetCashFlow());

        // fixedExpenseRatio = 600,000 / 3,000,000 = 0.2000
        assertEquals(
                BigDecimal.valueOf(0.2).setScale(4),
                result.getFixedExpenseRatio()
        );
    }

    /**
     * 총소득이 0원인 경우 고정지출 비율이 null인지 확인합니다.
     */
    @Test
    void calculateAndUpsert_whenIncomeIsZero_returnsNullRatio() {
        InMemoryDiagnosisResultMapper mapper =
                new InMemoryDiagnosisResultMapper();

        DiagnosisResultService service =
                new DiagnosisResultService(mapper);

        DiagnosisCalculationInput input =
                new DiagnosisCalculationInput(
                        1L,
                        "2026-07",
                        0L,
                        0L,
                        500_000L,
                        100_000L,
                        0L,
                        0L,
                        0L
                );

        DiagnosisResult result =
                service.calculateAndUpsert(input);

        // 소득이 없어도 순현금흐름은 계산합니다.
        assertEquals(-500_000L, result.getNetCashFlow());

        // totalIncome이 0이면 비율은 계산하지 않습니다.
        assertNull(result.getFixedExpenseRatio());
    }

    /**
     * 저장된 진단 결과가 없으면 DIAGNOSIS_RESULT_NOT_FOUND 예외가 발생하는지 확인합니다.
     */
    @Test
    void findByUserIdAndYearMonth_whenNotFound_throwsNotFound() {
        DiagnosisResultService service =
                new DiagnosisResultService(new InMemoryDiagnosisResultMapper());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.findByUserIdAndYearMonth(1L, "2026-07")
        );

        assertEquals(
                DiagnosisErrorCode.DIAGNOSIS_RESULT_NOT_FOUND.getStatusCode(),
                exception.getStatusCode()
        );
    }

    /**
     * "2026-13"처럼 자릿수는 맞지만 실제로는 존재하지 않는 연월을 거부하는지 확인합니다.
     */
    @Test
    void findByUserIdAndYearMonth_whenYearMonthIsCalendarInvalid_throwsInvalidRequest() {
        DiagnosisResultService service =
                new DiagnosisResultService(new InMemoryDiagnosisResultMapper());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.findByUserIdAndYearMonth(1L, "2026-13")
        );

        assertEquals(
                DiagnosisErrorCode.INVALID_REQUEST.getStatusCode(),
                exception.getStatusCode()
        );
    }

    /**
     * 저장된 진단 결과가 없으면 findLatestByUserId는 예외 없이 빈 목록을 반환합니다.
     */
    @Test
    void findLatestByUserId_whenNoResults_returnsEmptyList() {
        DiagnosisResultService service =
                new DiagnosisResultService(new InMemoryDiagnosisResultMapper());

        assertTrue(service.findLatestByUserId(1L, 3).isEmpty());
    }

    @Test
    void calculateAndUpsert_whenYearMonthIsCalendarInvalid_throwsIllegalArgumentException() {
        DiagnosisResultService service =
                new DiagnosisResultService(new InMemoryDiagnosisResultMapper());
        DiagnosisCalculationInput input = new DiagnosisCalculationInput(
                1L,
                "2026-13",
                3_000_000L,
                0L,
                2_000_000L,
                600_000L,
                5_000_000L,
                3_000_000L,
                2_000_000L
        );

        assertThrows(IllegalArgumentException.class, () -> service.calculateAndUpsert(input));
    }

    @Test
    void findLatestByUserId_whenLimitIsNotPositive_throwsInvalidRequest() {
        DiagnosisResultService service =
                new DiagnosisResultService(new InMemoryDiagnosisResultMapper());

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.findLatestByUserId(1L, 0)
        );

        assertEquals(DiagnosisErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    /**
     * 실제 DB 대신 Map을 사용하여 Mapper 동작을 흉내 내는 테스트용 구현체입니다.
     */
    private static class InMemoryDiagnosisResultMapper
            implements DiagnosisResultMapper {

        private final Map<String, DiagnosisResult> storage =
                new HashMap<>();

        @Override
        public int upsert(DiagnosisResult diagnosisResult) {
            String key = diagnosisResult.getUserId()
                    + "-"
                    + diagnosisResult.getYearMonth();

            storage.put(key, diagnosisResult);

            return 1;
        }

        @Override
        public DiagnosisResult findByUserIdAndYearMonth(
                Long userId,
                String yearMonth
        ) {
            return storage.get(userId + "-" + yearMonth);
        }

        @Override
        public List<DiagnosisResult> findLatestByUserId(
                Long userId,
                int limit
        ) {
            return storage.values().stream()
                    .filter(result -> result.getUserId().equals(userId))
                    .sorted(
                            Comparator.comparing(DiagnosisResult::getYearMonth)
                                    .reversed()
                    )
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }
}
