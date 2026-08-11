package com.ntropy.account.mapper;

import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;
import org.apache.ibatis.annotations.Mapper;

import com.ntropy.account.domain.entity.TxnAnalysis;

@Mapper
public interface TxnAnalysisMapper {

    /**
     * 거래 분석 결과를 저장하거나 갱신합니다.
     */
    int upsert(TxnAnalysis txnAnalysis);

    int upsertAnalyses(TransactionAnalysisSaveRequest request);
}