package com.ntropy.account.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ntropy.account.domain.AccountGroup;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF 보유계좌 조회 응답 1건을 저장하는 도메인 객체.
 * 계좌 원문 번호는 저장하지 않고 표시용 마스킹 값과 중복 판별용 해시만 가진다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Account {

    private Long id;
    private Long codefConnectionId;
    private Long userId;
    private String organizationCode;
    private AccountGroup accountGroup;
    private String depositTypeCode;
    private String accountNoMasked;
    private String accountNoHash;
    private String accountName;
    private BigDecimal balance;
    private String currencyCode;
    private LocalDate accountStartDate;
    private LocalDate accountEndDate;
    private LocalDate lastTranDate;

    // 예금/신탁 그룹 전용 (마이너스통장 대출 정보 포함)
    private String accountLifetime;
    private Boolean overdraftYn;
    private String loanKind;
    private BigDecimal loanBalance;
    private LocalDate loanStartDate;
    private LocalDate loanEndDate;

    // 펀드 그룹 전용
    private BigDecimal investedCost;
    private BigDecimal earningsRate;

    // 대출 그룹 전용
    private String loanExecNo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
