package com.ntropy.ai.dto.fastapi;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI 소비 분류 API에 전달할 거래 1건 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionForClassification {

    private Long transactionId;
    private String merchantName;
    private String description;
    private Long amount;
    private LocalDateTime transactionDate;
}