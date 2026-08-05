package com.ntropy.common.client;

import java.util.List;

import com.ntropy.common.dto.work.internal.PlatformDepositMatchCandidate;

/** 계좌 입금 거래 매칭에만 사용하는 플랫폼 내부 조회 계약. */
public interface PlatformDepositQueryClient {

    List<PlatformDepositMatchCandidate> getPlatformDepositMatchCandidates();
}
