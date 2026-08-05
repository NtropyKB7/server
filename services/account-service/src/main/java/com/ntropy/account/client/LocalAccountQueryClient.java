package com.ntropy.account.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialDataQueryMapper;
import com.ntropy.account.mapper.projection.OwnedAccountTransactionRow;
import com.ntropy.common.client.AccountQueryClient;
import com.ntropy.common.dto.account.AccountSummary;
import com.ntropy.common.dto.account.AccountTransactionSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalAccountQueryClient implements AccountQueryClient {

    private final FinancialDataQueryMapper financialDataQueryMapper;

    @Override
    public List<AccountSummary> findAccounts(Long userId) {
        requirePositive(userId, "userId");
        return financialDataQueryMapper.findAccountsByUserId(userId).stream()
                .map(LocalAccountQueryClient::toSummary)
                .toList();
    }

    @Override
    public AccountSummary findAccount(Long userId, Long accountId) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");

        Account account = financialDataQueryMapper.findAccountByIdAndUserId(accountId, userId);
        if (account == null) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
        return toSummary(account);
    }

    @Override
    public List<AccountTransactionSummary> findTransactions(Long userId, Long accountId) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");

        List<OwnedAccountTransactionRow> rows =
                financialDataQueryMapper.findTransactionsByAccountIdAndUserId(accountId, userId);
        if (rows.isEmpty()) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
        return rows.stream()
                .filter(row -> row.getTransactionId() != null)
                .map(LocalAccountQueryClient::toSummary)
                .toList();
    }

    private static AccountSummary toSummary(Account account) {
        return new AccountSummary(
                account.getId(), account.getOrganizationCode(), account.getAccountGroup().name(),
                account.getDepositTypeCode(), account.getAccountNoMasked(), account.getAccountName(),
                account.getBalance(), account.getCurrencyCode(), account.getAccountStartDate(),
                account.getLastTranDate(), account.getOverdraftYn(), account.getNextPaymentDate()
        );
    }

    private static AccountTransactionSummary toSummary(OwnedAccountTransactionRow row) {
        return new AccountTransactionSummary(
                row.getTransactionId(), row.getOwnedAccountId(), row.getTransactionCategory().name(),
                row.getTransactionDate(), row.getTransactionTime(), row.getOutAmount(), row.getInAmount(),
                row.getAfterBalance(), row.getDesc1(), row.getDesc2(), row.getDesc3(), row.getDesc4()
        );
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, fieldName + "는 양수여야 합니다.");
        }
    }
}
