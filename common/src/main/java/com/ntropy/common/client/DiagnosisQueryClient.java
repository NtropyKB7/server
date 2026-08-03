package com.ntropy.common.client;

import com.ntropy.common.dto.diagnosis.DiagnosisDefenseSnapshot;

/** diagnosis-service가 방어모드 계산에 제공하는 읽기 계약. */
public interface DiagnosisQueryClient {
    DiagnosisDefenseSnapshot getDefenseSnapshot(Long userId);
}
