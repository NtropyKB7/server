package com.ntropy.common.dto.account;

/** 계좌 등록 결과 요약. */
public record AccountRegistrationSummary(
        String connectionType,
        String organizationCode,
        int accountCount
) {
}
