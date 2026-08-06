package com.ntropy.work.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.Settlement;

@Mapper
public interface SettlementMapper {

    void insert(Settlement settlement);

    boolean existsByJobIdAndPeriod(@Param("jobId") Long jobId,
                                    @Param("periodStart") LocalDate periodStart,
                                    @Param("periodEnd") LocalDate periodEnd);
}
