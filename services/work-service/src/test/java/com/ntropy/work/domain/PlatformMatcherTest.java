package com.ntropy.work.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.entity.Platform;

class PlatformMatcherTest {

    private static Platform platform(long id, String depositName) {
        return Platform.builder().platformId(id).depositName(depositName).build();
    }

    @Test
    @DisplayName("정규화 후 정확히 하나의 PLATFORM과 일치하면 Matched를 반환한다")
    void match_singleMatch_returnsMatched() {
        List<Platform> platforms = List.of(
                platform(1L, "우아한형제들"),
                platform(2L, "쿠팡이츠")
        );

        PlatformMatchResult result = PlatformMatcher.match("우아한 형제들", platforms);

        PlatformMatchResult.Matched matched = assertInstanceOf(PlatformMatchResult.Matched.class, result);
        assertEquals(1L, matched.platform().getPlatformId());
    }

    @Test
    @DisplayName("일치하는 PLATFORM이 없으면 NotMatched를 반환한다")
    void match_noMatch_returnsNotMatched() {
        List<Platform> platforms = List.of(platform(1L, "우아한형제들"));

        PlatformMatchResult result = PlatformMatcher.match("알수없는입금처", platforms);

        assertInstanceOf(PlatformMatchResult.NotMatched.class, result);
    }

    @Test
    @DisplayName("정규화 후 동일해지는 PLATFORM이 여러 개면 Ambiguous를 반환한다")
    void match_multipleMatches_returnsAmbiguous() {
        List<Platform> platforms = List.of(
                platform(1L, "쿠팡이츠"),
                platform(2L, "쿠팡 이츠")
        );

        PlatformMatchResult result = PlatformMatcher.match("쿠팡이츠", platforms);

        PlatformMatchResult.Ambiguous ambiguous = assertInstanceOf(PlatformMatchResult.Ambiguous.class, result);
        assertEquals(2, ambiguous.candidates().size());
    }

    @Test
    @DisplayName("대소문자와 공백이 달라도 정규화되어 매칭된다")
    void match_normalizesBeforeComparing() {
        List<Platform> platforms = List.of(platform(1L, "Coupang Eats"));

        PlatformMatchResult result = PlatformMatcher.match("coupangeats", platforms);

        assertInstanceOf(PlatformMatchResult.Matched.class, result);
    }
}
