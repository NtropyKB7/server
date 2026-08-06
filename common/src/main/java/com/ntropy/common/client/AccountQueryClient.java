package com.ntropy.common.client;

import java.time.LocalDate;
import java.util.List;

import com.ntropy.common.dto.account.AccountSummary;
import com.ntropy.common.dto.account.AccountTransactionSummary;
import com.ntropy.common.dto.account.PageSummary;

/** 로그인 사용자가 소유한 계좌와 거래내역을 조회하는 account-service 계약. */
public interface AccountQueryClient {

    List<AccountSummary> findAccounts(Long userId);

    AccountSummary findAccount(Long userId, Long accountId);

    PageSummary<AccountTransactionSummary> findTransactions(
            Long userId,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );
}
