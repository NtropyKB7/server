package com.ntropy.ai.client.fastapi;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ntropy.ai.dto.fastapi.TransactionClassificationRequest;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;

import lombok.RequiredArgsConstructor;

/**
 * FastAPI 소비 분류 API 호출 Client입니다.
 */
@Component
@RequiredArgsConstructor
public class FastApiTransactionClassificationClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fastapi.base-url:http://localhost:8000}")
    private String fastApiBaseUrl;

    public TransactionClassificationResponse classifyTransactions(
            List<TransactionForClassification> transactions
    ) {
        String url = fastApiBaseUrl + "/api/v1/classify-transactions";

        TransactionClassificationRequest request =
                new TransactionClassificationRequest(transactions);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TransactionClassificationRequest> httpEntity =
                new HttpEntity<>(request, headers);

        return restTemplate.postForObject(
                url,
                httpEntity,
                TransactionClassificationResponse.class
        );
    }
}