package com.ntropy.common.client;

import java.time.LocalDate;
import java.util.List;

import com.ntropy.common.dto.account.internal.NormalizedIncomingTransaction;

/** account-service가 가공한 입금 거래를 다른 도메인에 제공하는 내부 조회 계약. */
public interface IncomingTransactionQueryClient {

    List<NormalizedIncomingTransaction> findIncomingTransactions(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
