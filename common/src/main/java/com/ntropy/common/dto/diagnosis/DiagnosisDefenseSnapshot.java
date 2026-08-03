package com.ntropy.common.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
/** diagnosis-service가 방어모드에 전달하는 D-Day 계산용 원본 데이터. */
public class DiagnosisDefenseSnapshot {
    /** 최신 재무진단의 즉시 사용할 수 있는 유동자산. */
    private Long liquidAssets;

    /** 최근 최대 3개 재무진단 total_expense의 평균. */
    private Long averageMonthlyExpense;
}
