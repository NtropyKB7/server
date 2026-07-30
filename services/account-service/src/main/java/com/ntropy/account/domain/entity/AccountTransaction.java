package com.ntropy.account.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF 수시입출 거래내역 조회 응답의 거래 1건을 저장하는 도메인 객체.
 * desc1~desc4는 은행마다 의미가 달라 원본 필드명을 그대로 보존한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountTransaction {

    private Long id;
    private Long accountId;
    private LocalDate tranDate;
    private LocalTime tranTime;
    private BigDecimal outAmount;
    private BigDecimal inAmount;
    private BigDecimal afterBalance;
    private String desc1;
    private String desc2;
    private String desc3;
    private String desc4;
    private LocalDateTime createdAt;
}
