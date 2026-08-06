package com.ntropy.work.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    private Long settlementId;
    private Long jobId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long expectedAmount;
    private Long actualAmount;
    private Long accountTransactionId;
    private LocalDateTime matchedAt;
}
