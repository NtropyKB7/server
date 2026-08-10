package com.ntropy.diagnosis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.dto.DiagnosisCalculationInput;
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
     * 읽기 전용 트랜잭션으로 실행합니다.
     */
    @Transactional(readOnly = true)
    public DiagnosisResult findByUserIdAndYearMonth(
            Long userId,
            String yearMonth
    ) {
        return diagnosisResultMapper.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );
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

        if (input.getYearMonth() == null
                || !input.getYearMonth().matches("\\d{4}-\\d{2}")) {
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
}