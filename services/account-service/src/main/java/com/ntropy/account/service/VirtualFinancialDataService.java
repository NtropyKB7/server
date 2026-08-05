package com.ntropy.account.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.AccountBalanceConsistencyValidator;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.AccountNoMask;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator.GeneratedTransactions;

import lombok.RequiredArgsConstructor;

/** account-service 테이블에 FIN-005 가상 금융 데이터셋을 멱등하게 저장한다. */
@Service
@RequiredArgsConstructor
public class VirtualFinancialDataService {

    public static final int USER_COUNT = 50;
    public static final int ACCOUNTS_PER_USER = 2;
    public static final int EXPECTED_TRANSACTION_COUNT = 15_000;

    private static final long LOGICAL_USER_ID_BASE = 9_000_046_000L;
    private static final String DATASET_VERSION = "FIN-005-v1";
    private static final String CURRENCY_KRW = "KRW";
    private static final int TRANSACTION_BATCH_SIZE = 500;

    private final VirtualConnectionService virtualConnectionService;
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;
    private final VirtualFinancialTransactionGenerator transactionGenerator;

    @Transactional
    public GenerationSummary generate() {
        int generatedAccounts = 0;
        int generatedTransactions = 0;
        PersonalBank[] banks = PersonalBank.values();

        for (int userOrdinal = 1; userOrdinal <= USER_COUNT; userOrdinal++) {
            Long userId = LOGICAL_USER_ID_BASE + userOrdinal;
            PersonalBank bank = banks[(userOrdinal - 1) % banks.length];
            CodefConnection connection = virtualConnectionService.getOrCreateConnection(userId);
            virtualConnectionService.registerInstitution(connection, bank.getOrganizationCode());

            Account ordinaryAccount = saveAccount(buildOrdinaryAccount(userOrdinal, userId, connection, bank));
            Account secondaryAccount = saveAccount(buildSecondaryAccount(userOrdinal, userId, connection, bank));
            generatedAccounts += 2;

            GeneratedTransactions generated = transactionGenerator.generate(
                    userOrdinal, bank, ordinaryAccount, secondaryAccount
            );
            generatedTransactions += generated.transactions().size();

            ordinaryAccount.setBalance(generated.finalBalances().get(ordinaryAccount.getId()));
            secondaryAccount.setBalance(generated.finalBalances().get(secondaryAccount.getId()));
            accountMapper.upsert(ordinaryAccount);
            accountMapper.upsert(secondaryAccount);

            insertInBatches(generated.transactions());
            validateStoredBalance(ordinaryAccount);
            validateStoredBalance(secondaryAccount);
        }

        if (generatedTransactions != EXPECTED_TRANSACTION_COUNT) {
            throw new IllegalStateException(
                    "가상 거래 총 건수 불일치: expected=" + EXPECTED_TRANSACTION_COUNT
                            + ", actual=" + generatedTransactions
            );
        }
        return new GenerationSummary(
                USER_COUNT, generatedAccounts, VirtualFinancialTransactionGenerator.INCOME_COUNTERPARTY_COUNT,
                generatedTransactions,
                VirtualFinancialTransactionGenerator.START_DATE,
                VirtualFinancialTransactionGenerator.END_DATE
        );
    }

    private Account saveAccount(Account account) {
        accountMapper.upsert(account);
        Account saved = accountMapper.findByConnectionIdAndAccountNoHash(
                account.getCodefConnectionId(), account.getAccountNoHash()
        );
        if (saved == null || saved.getId() == null) {
            throw new IllegalStateException("가상 금융계좌 저장 확인 실패");
        }
        return saved;
    }

    private static Account buildOrdinaryAccount(int userOrdinal, Long userId,
                                                CodefConnection connection, PersonalBank bank) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 1);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setAccountName(bank.getDisplayName() + " 가상 수시입출금");
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(false);
        return account;
    }

    private static Account buildSecondaryAccount(int userOrdinal, Long userId,
                                                 CodefConnection connection, PersonalBank bank) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 2);
        boolean installment = userOrdinal <= USER_COUNT / 2;
        account.setAccountGroup(installment ? AccountGroup.DEPOSIT_TRUST : AccountGroup.LOAN);
        account.setDepositTypeCode(installment ? "12" : "40");
        account.setAccountName(bank.getDisplayName() + (installment ? " 가상 적금" : " 가상 대출"));
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(null);
        account.setNextPaymentDate(LocalDate.of(2026, 7, 28));
        return account;
    }

    private static Account baseAccount(int userOrdinal, Long userId,
                                       CodefConnection connection, PersonalBank bank, int accountOrdinal) {
        String rawAccountNo = String.format(Locale.ROOT, "46%03d%07d", userOrdinal, accountOrdinal);
        Account account = new Account();
        account.setCodefConnectionId(connection.getId());
        account.setUserId(userId);
        account.setOrganizationCode(bank.getOrganizationCode());
        account.setAccountNoMasked(AccountNoMask.mask(rawAccountNo));
        account.setAccountNoHash(AccountNoHash.hash(
                bank.getOrganizationCode(), DATASET_VERSION + ":" + rawAccountNo
        ));
        account.setCurrencyCode(CURRENCY_KRW);
        account.setAccountStartDate(LocalDate.of(2025, 1, 1).plusDays(userOrdinal));
        account.setLastTranDate(VirtualFinancialTransactionGenerator.END_DATE);
        return account;
    }

    private void insertInBatches(List<AccountTransaction> transactions) {
        for (int start = 0; start < transactions.size(); start += TRANSACTION_BATCH_SIZE) {
            int end = Math.min(start + TRANSACTION_BATCH_SIZE, transactions.size());
            accountTransactionMapper.insertAll(transactions.subList(start, end));
        }
    }

    private void validateStoredBalance(Account account) {
        List<AccountTransaction> transactions = accountTransactionMapper.findByAccountIdAndDateRange(
                account.getId(),
                VirtualFinancialTransactionGenerator.START_DATE,
                VirtualFinancialTransactionGenerator.END_DATE
        );
        AccountBalanceConsistencyValidator.validate(account, transactions);
    }

    public record GenerationSummary(
            int users,
            int accounts,
            int incomeCounterparties,
            int transactions,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }
}
