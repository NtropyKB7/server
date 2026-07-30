package com.ntropy.payment.client.portone;

public interface PortOnePaymentClient {

    PortOnePaymentVerification verifyPayment(String paymentId);
    PortOnePaymentVerification payWithBillingKey(String paymentId, String billingKey, long amount, String orderName);
}