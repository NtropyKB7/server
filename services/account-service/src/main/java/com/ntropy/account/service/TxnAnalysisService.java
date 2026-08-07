package com.ntropy.account.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.TxnAnalysis;
import com.ntropy.account.mapper.TxnAnalysisMapper;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;
import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TxnAnalysisService {

    private final TxnAnalysisMapper txnAnalysisMapper;

    public List<ClassificationTargetTransaction> findClassificationTargets(
            Long userId,
            String yearMonth
    ) {
        validateUserId(userId);
        validateYearMonth(yearMonth);

        return txnAnalysisMapper.findClassificationTargets(userId, yearMonth);
    }

    @Transactional
    public void saveTransactionAnalyses(TransactionAnalysisSaveRequest request) {
        validateUserId(request.getUserId());
        validateYearMonth(request.getYearMonth());

        if (request.getResults() == null || request.getResults().isEmpty()) {
            return;
        }

        for (TransactionAnalysisSaveItem item : request.getResults()) {
            TxnAnalysis txnAnalysis = new TxnAnalysis();
            txnAnalysis.setAccountTransactionId(item.getTransactionId());
            txnAnalysis.setIsConsumption(item.getIsConsumption());
            txnAnalysis.setCategory(item.getCategory());
            txnAnalysis.setExpenseType(item.getExpenseType());

            txnAnalysisMapper.upsert(txnAnalysis);
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 양수여야 합니다.");
        }
    }

    private void validateYearMonth(String yearMonth) {
        if (yearMonth == null || !yearMonth.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("yearMonth는 YYYY-MM 형식이어야 합니다.");
        }
    }
}