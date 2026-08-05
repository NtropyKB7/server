package com.ntropy.work.client;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.PlatformDepositQueryClient;
import com.ntropy.common.dto.work.internal.PlatformDepositMatchCandidate;
import com.ntropy.work.service.PlatformService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalPlatformDepositQueryClient implements PlatformDepositQueryClient {

    private final PlatformService platformService;

    @Override
    public List<PlatformDepositMatchCandidate> getPlatformDepositMatchCandidates() {
        return platformService.findAll().stream()
                .map(platform -> new PlatformDepositMatchCandidate(
                        platform.getPlatformId(), platform.getDepositName()
                ))
                .toList();
    }
}
