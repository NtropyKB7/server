package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.PlatformMatchStatus;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.common.dto.work.internal.PlatformDepositMatchCandidate;

class VirtualFinancialDataServiceTest {

    @Test
    void generatesExpectedDatasetAndIsIdempotent() {
        InMemoryCodefConnectionMapper connectionMapper = new InMemoryCodefConnectionMapper();
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        InMemoryAccountTransactionMapper transactionMapper = new InMemoryAccountTransactionMapper();
        VirtualFinancialDataService service = new VirtualFinancialDataService(
                new VirtualConnectionService(connectionMapper),
                accountMapper,
                transactionMapper,
                new VirtualFinancialTransactionGenerator(),
                new PlatformMatchingService(transactionMapper, VirtualFinancialDataServiceTest::platforms)
        );

        GenerationSummary first = service.generate();
        GenerationSummary second = service.generate();

        assertEquals(50, first.users());
        assertEquals(100, first.accounts());
        assertEquals(11, first.platforms());
        assertEquals(15_000, first.transactions());
        assertEquals(first, second);
        assertEquals(50, connectionMapper.store.size());
        assertEquals(100, accountMapper.store.size());
        assertEquals(15_000, transactionMapper.store.size());
        assertEquals(11, transactionMapper.store.values().stream()
                .map(AccountTransaction::getPlatformId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count());
        assertEquals(1_500, transactionMapper.store.values().stream()
                .filter(transaction -> transaction.getPlatformMatchStatus() == PlatformMatchStatus.MATCHED)
                .count());
        assertEquals(0, transactionMapper.store.values().stream()
                .filter(transaction -> transaction.getPlatformMatchStatus() == PlatformMatchStatus.PENDING)
                .count());
    }

    private static List<PlatformDepositMatchCandidate> platforms() {
        List<String> depositNames = List.of(
                "우아한형제들", "쿠팡이츠", "위대한상상", "카카오모빌리티", "구글코리아",
                "로지올", "쿠팡풀필먼트서비스", "미소", "알바몬", "도그메이트", "엠브레인패널파워"
        );
        List<PlatformDepositMatchCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < depositNames.size(); index++) {
            candidates.add(new PlatformDepositMatchCandidate((long) index + 1, depositNames.get(index)));
        }
        return candidates;
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
        public Account findByIdAndProvider(Long id, String provider) {
            return store.values().stream().filter(account -> id.equals(account.getId())).findFirst().orElse(null);
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream().filter(account -> userId.equals(account.getUserId())).toList();
        }

        private static String key(Long connectionId, String accountNoHash) {
            return connectionId + ":" + accountNoHash;
        }
    }

    private static class InMemoryAccountTransactionMapper implements AccountTransactionMapper {

        private final Map<String, AccountTransaction> store = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            for (AccountTransaction transaction : transactions) {
                String key = transaction.getAccountId() + ":" + transaction.getFingerprint();
                AccountTransaction existing = store.get(key);
                if (existing == null) {
                    transaction.setId(nextId++);
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
        public List<AccountTransaction> findPendingPlatformMatches() {
            return store.values().stream()
                    .filter(transaction -> transaction.getPlatformMatchStatus() == PlatformMatchStatus.PENDING)
                    .filter(transaction -> transaction.getTransactionCategory()
                            == AccountTransactionCategory.ORDINARY)
                    .filter(transaction -> transaction.getInAmount().signum() > 0)
                    .toList();
        }

        @Override
        public int updatePlatformMatch(Long id, Long platformId, PlatformMatchStatus status) {
            return store.values().stream()
                    .filter(transaction -> id.equals(transaction.getId()))
                    .filter(transaction -> transaction.getPlatformMatchStatus() == PlatformMatchStatus.PENDING)
                    .findFirst()
                    .map(transaction -> {
                        transaction.setPlatformId(platformId);
                        transaction.setPlatformMatchStatus(status);
                        return 1;
                    })
                    .orElse(0);
        }
    }
}
