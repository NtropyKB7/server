package com.ntropy.payment.client.portone;

import com.ntropy.payment.config.PortOneProperties;
import com.ntropy.payment.domain.PaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class PortOnePaymentClientV2 implements PortOnePaymentClient {

    private static final String BASE_URL = "https://api.portone.io";

    private final PortOneProperties portOneProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public PortOnePaymentClientV2(PortOneProperties portOneProperties) {
        this.portOneProperties = portOneProperties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PortOnePaymentVerification verifyPayment(String paymentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "PortOne " + portOneProperties.getApiSecret());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/payments/" + paymentId,
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("포트원 응답이 비어있습니다. paymentId=" + paymentId);
        }

        boolean paid = "PAID".equals(body.get("status"));

        Object amountObj = body.get("amount");
        long amount = (amountObj instanceof Map)
                ? ((Number) ((Map<String, Object>) amountObj).get("total")).longValue()
                : ((Number) amountObj).longValue();

        Map<String, Object> methodMap = (Map<String, Object>) body.get("method");
        PaymentMethod paymentMethod = null;
        String paymentLabel = null;
        String paymentMasked = null;

        if (methodMap != null) {
            String type = String.valueOf(methodMap.get("type"));
            if (type.toLowerCase().contains("card")) {
                paymentMethod = PaymentMethod.CARD;
                Map<String, Object> card = (Map<String, Object>) methodMap.get("card");
                if (card != null) {
                    paymentLabel = (String) card.get("name");
                    paymentMasked = (String) card.get("number");
                }
            } else if (type.toLowerCase().contains("easypay")) {
                // 실제 응답 확인 결과 (2026-07-30, 카카오페이 라이브 테스트):
                // method.easyPay.provider가 아니라 method.provider가 바로 있음.
                // { "method": { "type": "PaymentMethodEasyPay", "provider": "KAKAOPAY", "easyPayMethod": {...} } }
                String provider = String.valueOf(methodMap.get("provider"));
                if (provider.toUpperCase().contains("KAKAO")) {
                    paymentMethod = PaymentMethod.KAKAOPAY;
                    paymentLabel = "카카오페이";
                } else if (provider.toUpperCase().contains("TOSS")) {
                    paymentMethod = PaymentMethod.TOSSPAY;
                    paymentLabel = "토스페이";
                }
                // 간편결제는 카드정보 자체가 안 넘어오므로 paymentMasked는 항상 null로 둔다
            }
        }

        String receiptUrl = (String) body.get("receiptUrl");

        return new PortOnePaymentVerification(paid, amount, paymentMethod, paymentLabel, paymentMasked, receiptUrl);
    }
}