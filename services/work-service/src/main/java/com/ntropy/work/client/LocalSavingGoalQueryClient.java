package com.ntropy.work.client;

import org.springframework.stereotype.Component;

import com.ntropy.common.client.SavingGoalQueryClient;
import com.ntropy.common.dto.work.summary.SavingGoalSummary;
import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.service.SavingGoalService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalSavingGoalQueryClient implements SavingGoalQueryClient {

    private final SavingGoalService savingGoalService;

    @Override
    public SavingGoalSummary findCurrentMonthGoal(Long userId) {
        SavingGoal savingGoal = savingGoalService.findCurrentMonthGoal(userId);
        if (savingGoal == null) {
            return null;
        }
        return SavingGoalSummary.builder()
                .savingGoalId(savingGoal.getSavingGoalId())
                .targetMonth(savingGoal.getTargetMonth())
                .targetAmount(savingGoal.getTargetAmount())
                .laborIntensity(savingGoal.getLaborIntensity())
                .build();
    }
}
