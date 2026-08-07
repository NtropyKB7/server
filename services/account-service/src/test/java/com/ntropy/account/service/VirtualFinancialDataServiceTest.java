package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;

class VirtualFinancialDataServiceTest {

    @Test
    void generatesExpectedDatasetAndIsIdempotent() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper();
        ZoneId zone = ZoneId.of("Asia/Seoul");
        // 월말을 기준일로 고정해 "현재 월"이 항상 완결된 3개월 창이 되도록 하고, 기존 고정 건수 검증값을 그대로 유지한다.
        Clock clock = Clock.fixed(LocalDate.of(2026, 6, 30).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper),
                accountMapper,
                transactionMapper,
                new VirtualFinancialTransactionGenerator(),
                clock
        );

        GenerationSummary first = service.generate();
        GenerationSummary second = service.generate();

        assertEquals(50, first.users());
        assertEquals(100, first.accounts());
        assertEquals(11, first.incomeCounterparties());
        assertEquals(15_000, first.transactions());
        assertEquals(first, second);
        assertEquals(50, connectionMapper.store.size());
        assertEquals(100, accountMapper.store.size());
        assertEquals(15_000, transactionMapper.store.size());

        // 사용자 순번 1은 적금(installment), 26은 대출(loan) 보조계좌를 받는다.
        Account installmentAccount = accountMapper.store.values().stream()
                .filter(account -> account.getUserId() == 9_000_046_001L
                        && account.getAccountGroup() == com.ntropy.account.domain.AccountGroup.DEPOSIT_TRUST
                        && "12".equals(account.getDepositTypeCode()))
                .findFirst().orElseThrow();
        assertEquals(null, installmentAccount.getLoanContractPrincipal());
        assertEquals(java.math.BigDecimal.valueOf(230, 2), installmentAccount.getInterestRate());
        assertEquals(LocalDate.of(2028, 6, 30), installmentAccount.getMaturityDate());
        assertEquals(LocalDate.of(2026, 7, 25), installmentAccount.getNextPaymentDate());
        assertEquals(lastTransactionDate(transactionMapper, installmentAccount), installmentAccount.getLastTranDate());

        Account loanAccount = accountMapper.store.values().stream()
                .filter(account -> account.getUserId() == 9_000_046_026L
                        && account.getAccountGroup() == com.ntropy.account.domain.AccountGroup.LOAN)
                .findFirst().orElseThrow();
        assertEquals(java.math.BigDecimal.valueOf(80_000_000L), loanAccount.getLoanContractPrincipal());
        assertEquals(java.math.BigDecimal.valueOf(340, 2), loanAccount.getInterestRate());
        assertEquals(LocalDate.of(2036, 6, 30), loanAccount.getMaturityDate());
        assertEquals(LocalDate.of(2026, 7, 25), loanAccount.getNextPaymentDate());
        assertEquals(lastTransactionDate(transactionMapper, loanAccount), loanAccount.getLastTranDate());
    }

    @Test
    void handlesLeapYearReferenceDateForProductConditionDatesWithoutError() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper();
        ZoneId zone = ZoneId.of("Asia/Seoul");
        // 2028-02-29(윤년)를 기준일로 삼아도 예외 없이 만기일·다음납입일이 계산돼야 한다.
        Clock clock = Clock.fixed(LocalDate.of(2028, 2, 29).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock
        );

        assertDoesNotThrow(() -> service.generateForUser(9_000_046_001L, com.ntropy.account.domain.PersonalBank.NH_BANK));

        Account loanOrInstallmentAccount = accountMapper.store.values().stream()
                .filter(account -> account.getNextPaymentDate() != null)
                .findFirst().orElseThrow();
        assertEquals(LocalDate.of(2028, 3, 25), loanOrInstallmentAccount.getNextPaymentDate());
        assertFalse(loanOrInstallmentAccount.getMaturityDate().isBefore(LocalDate.of(2028, 2, 29)));
    }

    @Test
    void usesCurrentMonthTwentyFifthBeforeDefaultPaymentDay() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper();
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(LocalDate.of(2026, 6, 15).atStartOfDay(zone).toInstant(), zone);
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper), accountMapper, transactionMapper,
                new VirtualFinancialTransactionGenerator(), clock
        );

        service.generateForUser(9_000_046_001L, com.ntropy.account.domain.PersonalBank.NH_BANK);

        Account secondaryAccount = accountMapper.store.values().stream()
                .filter(account -> account.getNextPaymentDate() != null)
                .findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 6, 25), secondaryAccount.getNextPaymentDate());
        assertEquals(lastTransactionDate(transactionMapper, secondaryAccount), secondaryAccount.getLastTranDate());
    }

    private static LocalDate lastTransactionDate(InMemoryAccountTransactionMapper transactionMapper,
                                                 Account account) {
        return transactionMapper.store.values().stream()
                .filter(transaction -> account.getId().equals(transaction.getAccountId()))
                .map(AccountTransaction::getTranDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
    }

    private static class InMemoryCodefConnectionMapper implements CodefConnectionMapper {

        private final Map<String, CodefConnection> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public void insert(CodefConnection connection) {
            upsert(connection);
        }

        @Override
        public void insertIfAbsent(CodefConnection connection) {
            store.computeIfAbsent(key(connection), ignored -> {
                connection.setId(nextId++);
                return connection;
            });
        }

        @Override
        public void upsert(CodefConnection connection) {
            CodefConnection existing = store.get(key(connection));
            if (existing == null) {
                connection.setId(nextId++);
            } else {
                connection.setId(existing.getId());
            }
            store.put(key(connection), connection);
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            return store.get(userId + ":" + provider);
        }

        private static String key(CodefConnection connection) {
            return connection.getUserId() + ":" + connection.getProvider();
        }
    }

    private static class InMemoryAccountMapper implements AccountMapper {

        private final Map<String, Account> store = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public void upsert(Account account) {
            String key = key(account.getCodefConnectionId(), account.getAccountNoHash());
            Account existing = store.get(key);
            if (existing == null) {
                account.setId(nextId++);
            } else {
                account.setId(existing.getId());
            }
            store.put(key, account);
        }

        @Override
        public void updateAccountDetails(Account account) {
        }

        @Override
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return store.get(key(codefConnectionId, accountNoHash));
        }

        @Override
        public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) {
            return store.values().stream()
                    .filter(account -> id.equals(account.getId()) && userId.equals(account.getUserId()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream().filter(account -> userId.equals(account.getUserId())).toList();
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
            store.values().removeIf(account -> userId.equals(account.getUserId()));
        }

        private static String key(Long connectionId, String accountNoHash) {
            return connectionId + ":" + accountNoHash;
        }
    }

    private static class InMemoryAccountTransactionMapper implements AccountTransactionMapper {

        private final Map<String, AccountTransaction> store = new LinkedHashMap<>();
        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            for (AccountTransaction transaction : transactions) {
                String key = transaction.getAccountId() + ":" + transaction.getFingerprint();
                AccountTransaction existing = store.get(key);
                if (existing == null) {
                    store.put(key, transaction);
                } else {
                    existing.setDesc1(existing.getDesc1() != null ? existing.getDesc1() : transaction.getDesc1());
                }
            }
        }

        @Override
        public List<AccountTransaction> findByAccountIdAndDateRange(Long accountId, LocalDate startDate,
                                                                    LocalDate endDate) {
            List<AccountTransaction> result = new ArrayList<>();
            for (AccountTransaction transaction : store.values()) {
                if (accountId.equals(transaction.getAccountId())
                        && !transaction.getTranDate().isBefore(startDate)
                        && !transaction.getTranDate().isAfter(endDate)) {
                    result.add(transaction);
                }
            }
            return result;
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
        }
    }
}
