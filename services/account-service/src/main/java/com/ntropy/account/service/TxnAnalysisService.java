package com.ntropy.account.service;

import java.util.List;

import com.ntropy.account.mapper.FinancialDataQueryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.entity.TxnAnalysis;
import com.ntropy.account.mapper.TxnAnalysisMapper;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;
import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TxnAnalysisService {

    private final TxnAnalysisMapper txnAnalysisMapper;
    private final FinancialDataQueryMapper financialDataQueryMapper;

    public List<ClassificationTargetTransaction> findClassificationTargets(
            Long userId,
            String yearMonth
    ) {
        return financialDataQueryMapper.findClassificationTargets(
                userId,
                yearMonth
        );
    }

    @Transactional
    public void saveAnalyses(TransactionAnalysisSaveRequest request) {
        if (request == null
                || request.getAnalyses() == null
                || request.getAnalyses().isEmpty()) {
            return;
        }

        txnAnalysisMapper.upsertAnalyses(request);
    }
}