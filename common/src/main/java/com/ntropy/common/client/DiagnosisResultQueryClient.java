package com.ntropy.common.client;

import com.ntropy.common.dto.diagnosis.DiagnosisResultSummary;

/**
 * diagnosis-service가 구현해야 하는 월별 재무진단 결과 조회 계약.
 *
 * <p>bff-service가 사용자·연월 기준 진단 결과를 프론트엔드에
 * 노출할 때 사용한다. 해당 연월의 진단 결과가 없으면 구현체가
 * DIAGNOSIS_RESULT_NOT_FOUND(404) 예외를 던진다.</p>
 */
public interface DiagnosisResultQueryClient {

    /** 사용자·연월 기준으로 진단 결과를 조회한다. */
    DiagnosisResultSummary findByUserIdAndYearMonth(Long userId, String yearMonth);
}
