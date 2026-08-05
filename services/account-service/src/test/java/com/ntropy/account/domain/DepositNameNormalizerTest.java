package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DepositNameNormalizerTest {

    @Test
    void normalizesWidthCaseWhitespaceAndSymbols() {
        assertEquals("coupang이츠123", DepositNameNormalizer.normalize(" ＣＯＵＰＡＮＧ-이츠 123 "));
    }

    @Test
    void removesJeonbukHomeBankingPrefix() {
        assertEquals("우아한형제들", DepositNameNormalizer.normalize(" 홈 ) 우아한 형제들 "));
    }

    @Test
    void returnsEmptyForMissingName() {
        assertEquals("", DepositNameNormalizer.normalize(null));
        assertEquals("", DepositNameNormalizer.normalize("  "));
    }
}
