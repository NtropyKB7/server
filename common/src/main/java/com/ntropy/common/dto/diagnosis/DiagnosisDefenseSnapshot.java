package com.ntropy.common.dto.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisDefenseSnapshot {
    private Long liquidAssets;
    private Long averageMonthlyExpense;
}
