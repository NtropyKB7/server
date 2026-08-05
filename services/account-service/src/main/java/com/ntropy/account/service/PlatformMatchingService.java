package com.ntropy.account.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.DepositNameNormalizer;
import com.ntropy.account.domain.PlatformMatchStatus;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.common.client.PlatformDepositQueryClient;
import com.ntropy.common.dto.work.internal.PlatformDepositMatchCandidate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformMatchingService {

    private final AccountTransactionMapper accountTransactionMapper;
    private final PlatformDepositQueryClient platformDepositQueryClient;

    @Transactional
    public MatchSummary matchPendingTransactions() {
        List<AccountTransaction> pendingTransactions = accountTransactionMapper.findPendingPlatformMatches();
        if (pendingTransactions.isEmpty()) {
            return new MatchSummary(0, 0, 0);
        }
        Map<String, List<PlatformDepositMatchCandidate>> candidatesByName = candidatesByNormalizedName();

        int matched = 0;
        int unmatched = 0;
        for (AccountTransaction transaction : pendingTransactions) {
            Set<Long> matchedPlatformIds = transactionDescriptionValues(transaction)
                    .map(DepositNameNormalizer::normalize)
                    .filter(name -> !name.isEmpty())
                    .flatMap(name -> candidatesByName.getOrDefault(name, List.of()).stream())
                    .map(PlatformDepositMatchCandidate::getPlatformId)
                    .collect(Collectors.toSet());

            Long platformId = matchedPlatformIds.size() == 1
                    ? matchedPlatformIds.iterator().next()
                    : null;
            PlatformMatchStatus status = platformId == null
                    ? PlatformMatchStatus.UNMATCHED
                    : PlatformMatchStatus.MATCHED;
            int updated = accountTransactionMapper.updatePlatformMatch(
                    transaction.getId(), platformId, status
            );
            if (updated == 0) {
                continue;
            }
            if (status == PlatformMatchStatus.MATCHED) {
                matched++;
            } else {
                unmatched++;
            }
        }
        return new MatchSummary(pendingTransactions.size(), matched, unmatched);
    }

    private static Stream<String> transactionDescriptionValues(AccountTransaction transaction) {
        return Stream.of(
                transaction.getDesc1(),
                transaction.getDesc2(),
                transaction.getDesc3(),
                transaction.getDesc4()
        ).filter(Objects::nonNull);
    }

    private Map<String, List<PlatformDepositMatchCandidate>> candidatesByNormalizedName() {
        List<PlatformDepositMatchCandidate> candidates =
                platformDepositQueryClient.getPlatformDepositMatchCandidates();
        if (candidates == null) {
            return Map.of();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.getPlatformId() != null)
                .filter(candidate -> !DepositNameNormalizer.normalize(candidate.getDepositName()).isEmpty())
                .collect(Collectors.groupingBy(
                        candidate -> DepositNameNormalizer.normalize(candidate.getDepositName())
                ));
    }

    public record MatchSummary(int pending, int matched, int unmatched) {
    }
}
