package com.ntropy.account.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.service.TxnAnalysisService;
import com.ntropy.common.client.AccountTransactionAnalysisClient;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalAccountTransactionAnalysisClient
        implements AccountTransactionAnalysisClient {

    private final TxnAnalysisService txnAnalysisService;

    @Override
    public List<ClassificationTargetTransaction> findClassificationTargets(
            Long userId,
            String yearMonth
    ) {
        return txnAnalysisService.findClassificationTargets(
                userId,
                yearMonth
        );
    }

    @Override
    public void saveTransactionAnalyses(
            TransactionAnalysisSaveRequest request
    ) {
        txnAnalysisService.saveAnalyses(request);
    }
}