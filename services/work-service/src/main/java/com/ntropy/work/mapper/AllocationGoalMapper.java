package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ntropy.work.domain.entity.AllocationGoal;

@Mapper
public interface AllocationGoalMapper {

    void insert(AllocationGoal allocationGoal);

    AllocationGoal findById(Long allocationGoalId);

    List<AllocationGoal> findByJobId(Long jobId);

    void update(AllocationGoal allocationGoal);

    void deleteById(Long allocationGoalId);
}
