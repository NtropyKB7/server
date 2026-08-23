package com.ntropy.defense.adapter.diagnosis;

import java.time.YearMonth;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.DiagnosisCommandClient;
import com.ntropy.defense.port.diagnosis.DiagnosisRecalculationPort;

import lombok.RequiredArgsConstructor;

/** diagnosis-service가 발행한 DiagnosisCommandClient를 defense의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class DiagnosisRecalculationAdapter implements DiagnosisRecalculationPort {

    private final DiagnosisCommandClient diagnosisCommandClient;

    @Override
    public void recalculate(Long userId, YearMonth yearMonth) {
        diagnosisCommandClient.recalculate(userId, yearMonth);
    }
}
