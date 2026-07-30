package com.ntropy.account.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * CODEF 웹 기반 개인 ID 로그인으로 연결할 수 있는 은행 목록.
 * 대구은행(0031)은 출금계좌번호와 출금계좌 비밀번호가 추가로 필요해 지원 대상에서 제외한다.
 */
public enum PersonalBank {

    IBK_INDUSTRIAL_BANK("0003", "기업은행", true, 5),
    KB_KOOKMIN_BANK("0004", "국민은행", true, 5),
    NH_BANK("0011", "농협은행", false, 5),
    SC_BANK("0023", "SC은행", false, null),
    JEONBUK_BANK("0037", "전북은행", false, 5),
    KYONGNAM_BANK("0039", "경남은행", false, 3),
    SAEMAUL_GEUMGO("0045", "새마을금고", false, 5),
    KEB_HANA_BANK("0081", "KEB하나은행", false, 5),
    SHINHAN_BANK("0088", "신한은행", false, 5);

    private final String organizationCode;
    private final String displayName;
    private final boolean birthDateRequired;
    private final Integer passwordErrorLimit;

    PersonalBank(String organizationCode, String displayName,
                 boolean birthDateRequired, Integer passwordErrorLimit) {
        this.organizationCode = organizationCode;
        this.displayName = displayName;
        this.birthDateRequired = birthDateRequired;
        this.passwordErrorLimit = passwordErrorLimit;
    }

    public String getOrganizationCode() {
        return organizationCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBirthDateRequired() {
        return birthDateRequired;
    }

    /**
     * SC은행처럼 ID/비밀번호 오류를 구분하지 않는 기관은 빈 값이다.
     */
    public Optional<Integer> getPasswordErrorLimit() {
        return Optional.ofNullable(passwordErrorLimit);
    }

    public static PersonalBank fromOrganizationCode(String organizationCode) {
        return Arrays.stream(values())
                .filter(bank -> bank.organizationCode.equals(organizationCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 은행 기관코드입니다: " + organizationCode
                ));
    }
}
