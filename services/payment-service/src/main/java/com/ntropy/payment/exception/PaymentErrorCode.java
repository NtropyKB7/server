package com.ntropy.payment.exception;

import com.ntropy.common.exception.ServiceErrorCode;
import lombok.Getter;


@Getter
public enum PaymentErrorCode implements ServiceErrorCode {

    SUBSCRIPTION_NOT_FOUND(404, "구독 정보를 찾을 수 없습니다."),
    ALREADY_CANCELLED(400, "이미 해지된 구독입니다."),
    ALREADY_SUBSCRIBED(409, "이미 구독 중입니다."),
    PAYMENT_NOT_COMPLETED(400, "결제가 완료되지 않았습니다."),
    INVALID_BILLING_KEY(400, "유효하지 않은 결제수단입니다."),
    PAYMENT_NOT_FOUND(404, "결제 예약 정보를 찾을 수 없습니다.");

    private final int statusCode;
    private final String message;

    PaymentErrorCode(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}