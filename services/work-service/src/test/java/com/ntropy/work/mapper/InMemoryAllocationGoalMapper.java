package com.ntropy.work.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.AllocationGoal;

/**
 * 테스트용 인메모리 AllocationGoalMapper 구현체.
 */
public class InMemoryAllocationGoalMapper implements AllocationGoalMapper {

    private final Map<Long, AllocationGoal> store = new LinkedHashMap<>();
    private long sequence = 1;

    @Override
    public void insert(AllocationGoal allocationGoal) {
        allocationGoal.setAllocationGoalId(sequence++);
        store.put(allocationGoal.getAllocationGoalId(), allocationGoal);
    }

    @Override
    public AllocationGoal findById(Long allocationGoalId) {
        return store.get(allocationGoalId);
    }

    @Override
    public List<AllocationGoal> findByJobId(Long jobId) {
        List<AllocationGoal> result = new ArrayList<>();
        for (AllocationGoal goal : store.values()) {
            if (jobId.equals(goal.getJobId())) {
                result.add(goal);
            }
        }
        return result;
    }

    @Override
    public List<AllocationGoal> findByJobIdsAndTargetMonth(List<Long> jobIds, String targetMonth) {
        List<AllocationGoal> result = new ArrayList<>();
        for (AllocationGoal goal : store.values()) {
            if (jobIds.contains(goal.getJobId()) && targetMonth.equals(goal.getTargetMonth())) {
                result.add(goal);
            }
        }
        return result;
    }

    @Override
    public void update(AllocationGoal allocationGoal) {
        store.put(allocationGoal.getAllocationGoalId(), allocationGoal);
    }

    @Override
    public void deleteById(Long allocationGoalId) {
        store.remove(allocationGoalId);
    }
}
