package com.ntropy.account.domain;

/**
 * CODEF 보유계좌 조회 응답의 {@code data} 하위 계좌 종류별 배열 구분.
 */
public enum AccountGroup {

    DEPOSIT_TRUST("resDepositTrust"),
    FOREIGN_CURRENCY("resForeignCurrency"),
    FUND("resFund"),
    LOAN("resLoan"),
    INSURANCE("resInsurance");

    private final String responseFieldName;

    AccountGroup(String responseFieldName) {
        this.responseFieldName = responseFieldName;
    }

    public String getResponseFieldName() {
        return responseFieldName;
    }
}
