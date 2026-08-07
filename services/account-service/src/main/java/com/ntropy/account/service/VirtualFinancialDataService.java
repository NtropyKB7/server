package com.ntropy.account.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private final Clock clock;

    @Transactional
    public GenerationSummary generate() {
        LocalDate referenceDate = LocalDate.now(clock);
        int generatedAccounts = 0;
        int generatedTransactions = 0;
        PersonalBank[] banks = PersonalBank.values();

        for (int userOrdinal = 1; userOrdinal <= USER_COUNT; userOrdinal++) {
            Long userId = LOGICAL_USER_ID_BASE + userOrdinal;
            PersonalBank bank = banks[(userOrdinal - 1) % banks.length];
            UserGenerationResult result = generateForUser(userId, bank, userOrdinal, false, referenceDate);
            generatedAccounts += result.accounts();
            generatedTransactions += result.transactions();
        }

        // 지난 2개월은 사용자당 200건 고정이고, 현재 월은 기준일까지만 생성돼 사용자당 0~100건이다.
        int minimumExpected = USER_COUNT * VirtualFinancialTransactionGenerator.TRANSACTIONS_PER_USER_PER_MONTH * 2;
        int maximumExpected = USER_COUNT * VirtualFinancialTransactionGenerator.TRANSACTIONS_PER_USER_PER_MONTH * 3;
        if (generatedTransactions < minimumExpected || generatedTransactions > maximumExpected) {
            throw new IllegalStateException(
                    "가상 거래 총 건수 불일치: expected=[" + minimumExpected + "," + maximumExpected + "]"
                            + ", actual=" + generatedTransactions
            );
        }
        return new GenerationSummary(
                USER_COUNT, generatedAccounts, VirtualFinancialTransactionGenerator.INCOME_COUNTERPARTY_COUNT,
                generatedTransactions,
                YearMonth.from(referenceDate).minusMonths(2).atDay(1),
                referenceDate
        );
    }

    /** 로그인 사용자 한 명에게 선택 은행의 가상계좌 2개와 목 거래를 멱등 생성한다. */
    @Transactional
    public GenerationSummary generateForUser(Long userId, PersonalBank bank) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다");
        }
        if (bank == null) {
            throw new IllegalArgumentException("은행이 필요합니다");
        }
        LocalDate referenceDate = LocalDate.now(clock);
        int ordinal = stableUserOrdinal(userId);
        UserGenerationResult result = generateForUser(userId, bank, ordinal, true, referenceDate);
        return new GenerationSummary(
                1, result.accounts(), result.incomeCounterparties(), result.transactions(),
                YearMonth.from(referenceDate).minusMonths(2).atDay(1),
                referenceDate
        );
    }

    private UserGenerationResult generateForUser(Long userId, PersonalBank bank, int userOrdinal,
                                                  boolean useUserIdProfile, LocalDate referenceDate) {
        CodefConnection connection = virtualConnectionService.getOrCreateConnection(userId);
        virtualConnectionService.registerInstitution(connection, bank.getOrganizationCode());

        Account ordinaryAccount = saveAccount(
                buildOrdinaryAccount(userOrdinal, userId, connection, bank, referenceDate)
        );
        Account secondaryAccount = saveAccount(
                buildSecondaryAccount(userOrdinal, userId, connection, bank, referenceDate)
        );
        GeneratedTransactions generated = useUserIdProfile
                ? transactionGenerator.generateForUser(referenceDate, userId, bank, ordinaryAccount, secondaryAccount)
                : transactionGenerator.generate(referenceDate, userOrdinal, bank, ordinaryAccount, secondaryAccount);

        ordinaryAccount.setBalance(generated.finalBalances().get(ordinaryAccount.getId()));
        secondaryAccount.setBalance(generated.finalBalances().get(secondaryAccount.getId()));
        ordinaryAccount.setLastTranDate(lastTransactionDate(ordinaryAccount.getId(), generated.transactions()));
        secondaryAccount.setLastTranDate(lastTransactionDate(secondaryAccount.getId(), generated.transactions()));
        accountMapper.upsert(ordinaryAccount);
        accountMapper.upsert(secondaryAccount);

        insertInBatches(generated.transactions());
        validateStoredBalance(ordinaryAccount, referenceDate);
        validateStoredBalance(secondaryAccount, referenceDate);
        return new UserGenerationResult(2, generated.transactions().size(), generated.userIncomeCounterpartyCount());
    }

    private static int stableUserOrdinal(Long userId) {
        int profileCount = VirtualFinancialTransactionGenerator.ConsumerProfile.values().length;
        int hash = Long.hashCode(userId);
        int profileIndex = Math.floorMod(hash, profileCount);
        int variation = Math.floorMod(hash / profileCount, 12);
        return 1 + profileIndex + profileCount * variation;
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
                                                CodefConnection connection, PersonalBank bank,
                                                LocalDate referenceDate) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 1, referenceDate);
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode("11");
        account.setAccountName(bank.getDisplayName() + " 가상 수시입출금");
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(false);
        // 입출금계좌는 이율이 적용되지 않아 interestRate=null로 둔다.
        return account;
    }

    private static Account buildSecondaryAccount(int userOrdinal, Long userId,
                                                 CodefConnection connection, PersonalBank bank,
                                                 LocalDate referenceDate) {
        Account account = baseAccount(userOrdinal, userId, connection, bank, 2, referenceDate);
        boolean installment = userOrdinal <= USER_COUNT / 2;
        account.setAccountGroup(installment ? AccountGroup.DEPOSIT_TRUST : AccountGroup.LOAN);
        account.setDepositTypeCode(installment ? "12" : "40");
        account.setAccountName(bank.getDisplayName() + (installment ? " 가상 적금" : " 가상 대출"));
        account.setBalance(BigDecimal.ZERO);
        account.setOverdraftYn(null);
        account.setNextPaymentDate(DefaultPaymentSchedule.nextAfter(referenceDate));
        if (installment) {
            account.setInterestRate(installmentInterestRate(userOrdinal));
            account.setMaturityDate(referenceDate.plusYears(2));
        } else {
            account.setLoanContractPrincipal(loanContractPrincipal(userOrdinal));
            account.setInterestRate(loanInterestRate(userOrdinal));
            account.setMaturityDate(referenceDate.plusYears(10));
        }
        return account;
    }

    private static Account baseAccount(int userOrdinal, Long userId,
                                       CodefConnection connection, PersonalBank bank, int accountOrdinal,
                                       LocalDate referenceDate) {
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
        return account;
    }

    private static LocalDate lastTransactionDate(Long accountId, List<AccountTransaction> transactions) {
        return transactions.stream()
                .filter(transaction -> accountId.equals(transaction.getAccountId()))
                .map(AccountTransaction::getTranDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /** 적금 고정이율: 2.00~3.20% 사이에서 사용자 순번에 따라 결정적으로 배정한다. */
    private static BigDecimal installmentInterestRate(int userOrdinal) {
        return BigDecimal.valueOf(200 + (userOrdinal % 5) * 30, 2);
    }

    /** 대출 고정이율: 3.00~4.60% 사이에서 사용자 순번에 따라 결정적으로 배정한다. */
    private static BigDecimal loanInterestRate(int userOrdinal) {
        return BigDecimal.valueOf(300 + (userOrdinal % 5) * 40, 2);
    }

    private static BigDecimal loanContractPrincipal(int userOrdinal) {
        return BigDecimal.valueOf(50_000_000L + (userOrdinal % 10) * 5_000_000L);
    }

    private void insertInBatches(List<AccountTransaction> transactions) {
        for (int start = 0; start < transactions.size(); start += TRANSACTION_BATCH_SIZE) {
            int end = Math.min(start + TRANSACTION_BATCH_SIZE, transactions.size());
            accountTransactionMapper.insertAll(transactions.subList(start, end));
        }
    }

    private void validateStoredBalance(Account account, LocalDate referenceDate) {
        List<AccountTransaction> transactions = accountTransactionMapper.findByAccountIdAndDateRange(
                account.getId(),
                YearMonth.from(referenceDate).minusMonths(2).atDay(1),
                referenceDate
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

    private record UserGenerationResult(int accounts, int transactions, int incomeCounterparties) {
    }
}
