package com.ntropy.account.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialDataQueryMapper;
import com.ntropy.account.mapper.TxnAnalysisMapper;
import com.ntropy.common.dto.account.ClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;
import com.ntropy.common.dto.account.TransactionAnalysisSaveRequest;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TxnAnalysisService {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

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

        Long userId = request.getUserId();
        String yearMonth = request.getYearMonth();

        if (userId == null || userId <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "userId가 유효하지 않습니다.");
        }
        if (yearMonth == null || !YEAR_MONTH_PATTERN.matcher(yearMonth).matches()) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "yearMonth 형식이 올바르지 않습니다.");
        }

        Set<Long> requestedTransactionIds = new LinkedHashSet<>();
        for (TransactionAnalysisSaveItem item : request.getAnalyses()) {
            if (item == null) {
                throw new ServiceException(
                        AccountErrorCode.INVALID_REQUEST,
                        "거래 분석 항목이 필요합니다."
                );
            }

            Long transactionId = item.getTransactionId();
            if (transactionId == null || transactionId <= 0) {
                throw new ServiceException(
                        AccountErrorCode.INVALID_REQUEST,
                        "transactionId는 양수여야 합니다."
                );
            }

            if (!requestedTransactionIds.add(transactionId)) {
                throw new ServiceException(
                        AccountErrorCode.INVALID_REQUEST,
                        "중복된 transactionId가 포함되어 있습니다."
                );
            }
        }

        List<Long> validTransactionIds = financialDataQueryMapper.findValidTransactionIds(
                userId, yearMonth, List.copyOf(requestedTransactionIds));

        if (!new LinkedHashSet<>(validTransactionIds).equals(requestedTransactionIds)) {
            throw new ServiceException(AccountErrorCode.TRANSACTION_ANALYSIS_TARGET_INVALID);
        }

        txnAnalysisMapper.upsertAnalyses(request);
    }
}
