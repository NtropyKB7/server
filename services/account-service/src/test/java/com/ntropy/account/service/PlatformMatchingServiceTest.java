package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.PlatformMatchStatus;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.service.PlatformMatchingService.MatchSummary;
import com.ntropy.common.dto.work.internal.PlatformDepositMatchCandidate;

class PlatformMatchingServiceTest {

    @Test
    void searchesAllBankDescriptionFieldsAndMatchesOnlyOneUniquePlatform() {
        AccountTransaction desc1Match = pending(1L);
        desc1Match.setDesc1(" 쿠팡-이츠 ");
        AccountTransaction noMatch = pending(2L);
        noMatch.setDesc4("없는 거래처");
        AccountTransaction multipleMatches = pending(3L);
        multipleMatches.setDesc2("홈) 우아한 형제들");
        InMemoryTransactionMapper mapper = new InMemoryTransactionMapper(List.of(
                desc1Match, noMatch, multipleMatches
        ));
        PlatformMatchingService service = new PlatformMatchingService(mapper, () -> List.of(
                new PlatformDepositMatchCandidate(1L, "우아한형제들"),
                new PlatformDepositMatchCandidate(2L, "쿠팡이츠"),
                new PlatformDepositMatchCandidate(12L, "우아한 형제들")
        ));

        MatchSummary summary = service.matchPendingTransactions();

        assertEquals(new MatchSummary(3, 1, 2), summary);
        assertEquals(2L, mapper.transactions.get(0).getPlatformId());
        assertEquals(PlatformMatchStatus.MATCHED,
                mapper.transactions.get(0).getPlatformMatchStatus());
        assertNull(mapper.transactions.get(1).getPlatformId());
        assertEquals(PlatformMatchStatus.UNMATCHED,
                mapper.transactions.get(1).getPlatformMatchStatus());
        assertNull(mapper.transactions.get(2).getPlatformId());
        assertEquals(PlatformMatchStatus.UNMATCHED,
                mapper.transactions.get(2).getPlatformMatchStatus());
    }

    @Test
    void doesNotProcessCompletedTransactionsAgain() {
        AccountTransaction pending = pending(1L);
        pending.setDesc3("미소");
        InMemoryTransactionMapper mapper = new InMemoryTransactionMapper(List.of(
                pending
        ));
        PlatformMatchingService service = new PlatformMatchingService(mapper, () -> List.of(
                new PlatformDepositMatchCandidate(8L, "미소")
        ));

        assertEquals(new MatchSummary(1, 1, 0), service.matchPendingTransactions());
        assertEquals(new MatchSummary(0, 0, 0), service.matchPendingTransactions());
        assertEquals(8L, mapper.transactions.get(0).getPlatformId());
    }

    private static AccountTransaction pending(Long id) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setId(id);
        transaction.setAccountId(10L);
        transaction.setTransactionCategory(AccountTransactionCategory.ORDINARY);
        transaction.setPlatformMatchStatus(PlatformMatchStatus.PENDING);
        return transaction;
    }

    private static class InMemoryTransactionMapper implements AccountTransactionMapper {

        private final List<AccountTransaction> transactions;

        InMemoryTransactionMapper(List<AccountTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
        }

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            this.transactions.addAll(transactions);
        }

        @Override
        public List<AccountTransaction> findByAccountIdAndDateRange(Long accountId, LocalDate startDate,
                                                                    LocalDate endDate) {
            return List.of();
        }

        @Override
        public List<AccountTransaction> findPendingPlatformMatches() {
            return transactions.stream()
                    .filter(transaction -> transaction.getPlatformMatchStatus() == PlatformMatchStatus.PENDING)
                    .toList();
        }

        @Override
        public int updatePlatformMatch(Long id, Long platformId, PlatformMatchStatus status) {
            return transactions.stream()
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
