package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.Settlement;
import com.ntropy.work.domain.enums.SettlementMatchStatus;

@Mapper
public interface SettlementMapper {

    void insert(Settlement settlement);

    /** 이 거래(accountTransactionId)가 이미 MATCHED로 처리됐는지 - 배치 재실행 중복 방지용. */
    boolean existsByAccountTransactionId(@Param("accountTransactionId") Long accountTransactionId);

    boolean existsByUserIdAndStatusAndPeriod(@Param("userId") Long userId,
                                              @Param("status") SettlementMatchStatus status,
                                              @Param("periodStart") LocalDate periodStart,
                                              @Param("periodEnd") LocalDate periodEnd);

    List<Settlement> findByUserIdAndDepositDateRange(@Param("userId") Long userId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);
}
