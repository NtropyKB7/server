package com.ntropy.account.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.domain.TxnAnalysis;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;

@Mapper
public interface TxnAnalysisMapper {

    /**
     * 특정 사용자와 연월 기준으로 분류 대상 출금 거래를 조회합니다.
     */
    List<ClassificationTargetTransaction> findClassificationTargets(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth
    );

    /**
     * 거래 분석 결과를 저장하거나 갱신합니다.
     */
    int upsert(TxnAnalysis txnAnalysis);
}