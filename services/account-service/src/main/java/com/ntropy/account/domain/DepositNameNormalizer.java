package com.ntropy.account.domain;

import java.text.Normalizer;

/** 은행 원본 설명 필드와 플랫폼 입금처명을 보수적인 완전 일치 비교 형태로 정규화한다. */
public final class DepositNameNormalizer {

    /** 은행 채널 표기가 거래처명 앞에 붙는 확인된 형식. 현재 전북은행의 홈뱅킹 표기를 처리한다. */
    private static final String BANKING_CHANNEL_PREFIX = "^\\s*홈\\s*\\)";

    private DepositNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceFirst(BANKING_CHANNEL_PREFIX, "");
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
