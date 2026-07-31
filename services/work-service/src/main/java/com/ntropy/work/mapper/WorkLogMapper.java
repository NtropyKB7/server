package com.ntropy.work.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.work.domain.entity.WorkLog;

@Mapper
public interface WorkLogMapper {

    void insert(WorkLog workLog);

    WorkLog findById(Long logId);

    List<WorkLog> findByJobId(Long jobId);

    List<WorkLog> findByUserIdAndWorkDate(@Param("userId") Long userId, @Param("workDate") LocalDate workDate);

    List<WorkLog> findByUserIdAndDateRange(@Param("userId") Long userId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    void update(WorkLog workLog);

    void deleteById(Long logId);
}
