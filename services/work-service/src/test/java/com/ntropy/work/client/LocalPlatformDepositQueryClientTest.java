package com.ntropy.work.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.common.dto.work.internal.PlatformDepositMatchCandidate;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.mapper.InMemoryPlatformMapper;
import com.ntropy.work.service.PlatformService;

class LocalPlatformDepositQueryClientTest {

    @Test
    void returnsOnlyInternalMatchingFields() {
        InMemoryPlatformMapper mapper = new InMemoryPlatformMapper();
        mapper.seed(Platform.builder()
                .platformId(2L)
                .categoryId(1L)
                .platformName("쿠팡이츠")
                .depositName("쿠팡이츠서비스")
                .settlementCycle("DAILY")
                .build());
        LocalPlatformDepositQueryClient client = new LocalPlatformDepositQueryClient(
                new PlatformService(mapper)
        );

        List<PlatformDepositMatchCandidate> candidates = client.getPlatformDepositMatchCandidates();

        assertEquals(1, candidates.size());
        assertEquals(2L, candidates.get(0).getPlatformId());
        assertEquals("쿠팡이츠서비스", candidates.get(0).getDepositName());
    }
}
