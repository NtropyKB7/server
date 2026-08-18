package com.ntropy.common.domain;

/**
 * 배치가 대상으로 삼을 사용자 범위. {@code USERS.provider}가 가상회원 규칙(NTROPY_TEST)인지 여부로
 * 실사용자/가상회원을 나눈다 - 실제 provider 문자열 자체는 도메인 중립적이지 않으므로 common에 두지 않고
 * user-service 구현체 내부에서만 다룬다.
 */
public enum UserScope {
    REAL_ONLY,
    VIRTUAL_ONLY,
    ALL;

    /**
     * {@code .properties} 설정값을 UserScope로 변환한다. 알 수 없는 값을 ALL로 완화하지 않고
     * 항상 예외를 던져 애플리케이션 기동을 막는다(fail-closed).
     */
    public static UserScope fromConfigValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalStateException("user-scope 설정값이 비어 있습니다.");
        }
        try {
            return UserScope.valueOf(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "알 수 없는 user-scope 값입니다: " + rawValue + " (허용값: REAL_ONLY, VIRTUAL_ONLY, ALL)", e
            );
        }
    }
}
