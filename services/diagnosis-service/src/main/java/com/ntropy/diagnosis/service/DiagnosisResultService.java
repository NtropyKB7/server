package com.ntropy.diagnosis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.dto.DiagnosisCalculationInput;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;

import lombok.RequiredArgsConstructor;

/**
 * 월별 재무 진단 계산과 저장·조회를 담당하는 Service입니다.
 */
@Service
@RequiredArgsConstructor
public class DiagnosisResultService {

    private final DiagnosisResultMapper diagnosisResultMapper;

    /**
     * 입력값을 검증하고 월별 진단 결과를 계산하여 저장합니다.
     *
     * 계산 규칙:
     * - netCashFlow = totalIncome - totalExpense
     * - fixedExpenseRatio = fixedExpense / totalIncome
     * - totalIncome이 0이면 fixedExpenseRatio는 null
     *
     * @param input 진단 계산 입력값
     * @return 계산된 진단 결과
     */
    @Transactional
    public DiagnosisResult calculateAndUpsert(
            DiagnosisCalculationInput input
    ) {
        validate(input);

        // 총소득에서 총소비를 차감하여 순현금흐름을 계산합니다.
        long netCashFlow =
                input.getTotalIncome() - input.getTotalExpense();

        BigDecimal fixedExpenseRatio = null;

        // 총소득이 0보다 클 때만 고정지출 비율을 계산합니다.
        if (input.getTotalIncome() > 0) {
            fixedExpenseRatio = BigDecimal.valueOf(
                            input.getFixedExpense()
                    )
                    .divide(
                            BigDecimal.valueOf(input.getTotalIncome()),
                            4,
                            RoundingMode.HALF_UP
                    );
        }

        // 계산 결과를 DIAGNOSIS_RESULT 도메인 객체로 생성합니다.
        DiagnosisResult diagnosisResult =
                new DiagnosisResult(
                        null,
                        input.getUserId(),
                        input.getYearMonth(),
                        input.getTotalIncome(),
                        input.getUnmatchedIncome(),
                        input.getTotalExpense(),
                        netCashFlow,
                        input.getFixedExpense(),
                        fixedExpenseRatio,
                        input.getTotalFinancialAssets(),
                        input.getLiquidAssets(),
                        input.getSafeAssets(),
                        null,
                        null,
                        null
                );

        // (user_id, year_month) 기준으로 신규 저장 또는 갱신합니다.
        diagnosisResultMapper.upsert(diagnosisResult);

        return diagnosisResult;
    }

    /**
     * 사용자·연월 기준으로 진단 결과를 조회합니다.
     *
     * userId·yearMonth를 검증하고, 해당 진단 결과가 없으면
     * DIAGNOSIS_RESULT_NOT_FOUND 예외를 발생시킵니다.
     * 읽기 전용 트랜잭션으로 실행합니다.
     */
    @Transactional(readOnly = true)
    public DiagnosisResult findByUserIdAndYearMonth(
            Long userId,
            String yearMonth
    ) {
        validateUserId(userId);
        validateYearMonthForQuery(yearMonth);

        DiagnosisResult result = diagnosisResultMapper.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );

        if (result == null) {
            throw new ServiceException(DiagnosisErrorCode.DIAGNOSIS_RESULT_NOT_FOUND);
        }

        return result;
    }

    /**
     * 사용자 기준으로 연월이 최신인 진단 결과부터 최대 limit건을 조회합니다.
     *
     * 결과가 없으면 빈 목록을 반환합니다 (예외를 발생시키지 않습니다).
     * 읽기 전용 트랜잭션으로 실행합니다.
     */
    @Transactional(readOnly = true)
    public List<DiagnosisResult> findLatestByUserId(
            Long userId,
            int limit
    ) {
        validateUserId(userId);
        if (limit <= 0) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "limit은 양수여야 합니다."
            );
        }

        return diagnosisResultMapper.findLatestByUserId(userId, limit);
    }

    /**
     * 조회 계약에서 공통으로 사용하는 userId 검증입니다.
     */
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "userId는 양수여야 합니다."
            );
        }
    }

    /**
     * 조회 계약에서 사용하는 yearMonth 검증입니다.
     *
     * YearMonth.parse()로 파싱해, "2026-13"처럼 자릿수는 맞지만
     * 실제로는 존재하지 않는 연월도 거부합니다.
     */
    private void validateYearMonthForQuery(String yearMonth) {
        if (yearMonth == null) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "yearMonth는 필수입니다."
            );
        }

        if (!isValidYearMonth(yearMonth)) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "yearMonth는 YYYY-MM 형식이어야 합니다."
            );
        }
    }

    /**
     * 진단 계산에 필요한 필수 입력값을 검증합니다.
     */
    private void validate(DiagnosisCalculationInput input) {
        if (input == null) {
            throw new IllegalArgumentException(
                    "진단 계산 입력값은 필수입니다."
            );
        }

        if (input.getUserId() == null || input.getUserId() <= 0) {
            throw new IllegalArgumentException(
                    "userId는 양수여야 합니다."
            );
        }

        if (!isValidYearMonth(input.getYearMonth())) {
            throw new IllegalArgumentException(
                    "yearMonth는 YYYY-MM 형식이어야 합니다."
            );
        }

        // DIAGNOSIS_RESULT의 필수 컬럼에 저장되는 값들을 검증합니다.
        if (input.getTotalIncome() == null
                || input.getUnmatchedIncome() == null
                || input.getTotalExpense() == null
                || input.getFixedExpense() == null
                || input.getTotalFinancialAssets() == null
                || input.getLiquidAssets() == null
                || input.getSafeAssets() == null) {
            throw new IllegalArgumentException(
                    "진단 계산에 필요한 값이 누락되었습니다."
            );
        }
    }

    private boolean isValidYearMonth(String yearMonth) {
        if (yearMonth == null || !yearMonth.matches("[0-9]{4}-[0-9]{2}")) {
            return false;
        }
        try {
            YearMonth.parse(yearMonth);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
