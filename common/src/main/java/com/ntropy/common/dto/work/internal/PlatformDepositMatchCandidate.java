package com.ntropy.common.dto.work.internal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 외부 응답에 노출하지 않는 플랫폼 입금처명 매칭 후보. */
@Getter
@AllArgsConstructor
public class PlatformDepositMatchCandidate {

    private final Long platformId;
    private final String depositName;
}
