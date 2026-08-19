package com.ntropy.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserScopeTest {

    @Test
    void fromConfigValue_parsesKnownValues() {
        assertEquals(UserScope.REAL_ONLY, UserScope.fromConfigValue("REAL_ONLY"));
        assertEquals(UserScope.VIRTUAL_ONLY, UserScope.fromConfigValue("VIRTUAL_ONLY"));
        assertEquals(UserScope.ALL, UserScope.fromConfigValue("ALL"));
    }

    @Test
    void fromConfigValue_trimsSurroundingWhitespace() {
        assertEquals(UserScope.REAL_ONLY, UserScope.fromConfigValue("  REAL_ONLY  "));
    }

    @Test
    void fromConfigValue_unknownValue_doesNotFallBackToAllAndThrows() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> UserScope.fromConfigValue("EVERYONE")
        );
        assertEquals(
                "알 수 없는 user-scope 값입니다: EVERYONE (허용값: REAL_ONLY, VIRTUAL_ONLY, ALL)",
                exception.getMessage()
        );
    }

    @Test
    void fromConfigValue_lowercaseValue_isNotAccepted() {
        // enum 상수는 대소문자를 구분하므로, 설정 실수로 소문자를 넣으면 조용히 통과하지 않고 fail-closed된다.
        assertThrows(IllegalStateException.class, () -> UserScope.fromConfigValue("real_only"));
    }

    @Test
    void fromConfigValue_blankValue_throws() {
        assertThrows(IllegalStateException.class, () -> UserScope.fromConfigValue(""));
        assertThrows(IllegalStateException.class, () -> UserScope.fromConfigValue("   "));
        assertThrows(IllegalStateException.class, () -> UserScope.fromConfigValue(null));
    }
}
