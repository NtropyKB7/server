package com.ntropy.payment.exception;

import com.ntropy.common.exception.ServiceErrorCode;
import lombok.Getter;

@Getter
public enum PaymentErrorCode implements ServiceErrorCode {

    SUBSCRIPTION_NOT_FOUND(404, "구독 정보를 찾을 수 없습니다."),
    ALREADY_CANCELLED(400, "이미 해지된 구독입니다."),
    DUPLICATE_PAYMENT(409, "이미 처리된 결제입니다."),
    PAYMENT_NOT_COMPLETED(400, "결제가 완료되지 않았습니다."),
    AMOUNT_MISMATCH(400, "결제 금액이 올바르지 않습니다.");

    private final int statusCode;
    private final String message;

    PaymentErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}