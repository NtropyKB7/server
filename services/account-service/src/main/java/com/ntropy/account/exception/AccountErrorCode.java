package com.ntropy.account.exception;

import com.ntropy.common.exception.ServiceErrorCode;

import lombok.Getter;

@Getter
public enum AccountErrorCode implements ServiceErrorCode {

    UNSUPPORTED_BANK(400, "지원하지 않는 은행 기관코드입니다."),
    INVALID_REQUEST(400, "요청 값이 올바르지 않습니다."),
    ACCOUNT_NOT_FOUND(404, "계좌를 찾을 수 없습니다.");

    private final int statusCode;
    private final String message;

    AccountErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
