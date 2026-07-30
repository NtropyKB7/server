package com.ntropy.payment.client.portone;

public interface PortOnePaymentClient {

    PortOnePaymentVerification verifyPayment(String paymentId);
}