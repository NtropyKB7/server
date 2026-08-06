package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ntropy.work.domain.entity.Settlement;

/**
 * 테스트용 인메모리 SettlementMapper 구현체.
 */
public class InMemorySettlementMapper implements SettlementMapper {

    private final List<Settlement> store = new ArrayList<>();
    private long sequence = 1;

    @Override
    public void insert(Settlement settlement) {
        settlement.setSettlementId(sequence++);
        store.add(settlement);
    }

    @Override
    public boolean existsByJobIdAndPeriod(Long jobId, LocalDate periodStart, LocalDate periodEnd) {
        return store.stream().anyMatch(settlement ->
                settlement.getJobId().equals(jobId)
                        && settlement.getPeriodStart().equals(periodStart)
                        && settlement.getPeriodEnd().equals(periodEnd));
    }

    public List<Settlement> findAll() {
        return store;
    }
}
