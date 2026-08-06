package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformNameNormalizerTest {

    @Test
    @DisplayName("공백이 포함된 거래명은 공백이 제거되어 정규화된다")
    void normalize_removesWhitespace() {
        assertEquals("우아한형제들", PlatformNameNormalizer.normalize("우아한 형제들"));
    }

    @Test
    @DisplayName("영문 거래명은 소문자로 정규화된다")
    void normalize_lowercasesEnglish() {
        assertEquals("coupangeats", PlatformNameNormalizer.normalize("Coupang Eats"));
    }

    @Test
    @DisplayName("괄호 등 기호가 포함된 거래명은 기호가 제거되어 정규화된다")
    void normalize_removesSymbols() {
        assertEquals("쿠팡이츠", PlatformNameNormalizer.normalize("쿠팡(이츠)"));
    }

    @Test
    @DisplayName("null 입력은 빈 문자열로 정규화된다")
    void normalize_nullReturnsEmptyString() {
        assertEquals("", PlatformNameNormalizer.normalize(null));
    }
}
