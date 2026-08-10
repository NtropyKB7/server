package com.ntropy.diagnosis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.diagnosis.domain.entity.DiagnosisResult;

/**
 * DIAGNOSIS_RESULT 테이블 접근을 담당하는 MyBatis Mapper입니다.
 */
@Mapper
public interface DiagnosisResultMapper {

    /**
     * 사용자·연월 기준으로 진단 결과를 저장하거나 갱신합니다.
     *
     * @param diagnosisResult 저장할 진단 결과
     * @return 영향받은 행 수
     */
    int upsert(DiagnosisResult diagnosisResult);

    /**
     * 사용자·연월 기준으로 진단 결과를 조회합니다.
     *
     * @param userId 사용자 ID
     * @param yearMonth 기준 연월
     * @return 진단 결과
     */
    DiagnosisResult findByUserIdAndYearMonth(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth
    );
}