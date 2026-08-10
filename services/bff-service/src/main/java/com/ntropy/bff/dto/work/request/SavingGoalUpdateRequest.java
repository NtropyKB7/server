package com.ntropy.bff.dto.work.request;

import com.ntropy.common.dto.work.command.SavingGoalUpdateCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SavingGoalUpdateRequest {

    private Long targetAmount;
    private Long laborIntensity;

    public SavingGoalUpdateCommand toCommand() {
        return new SavingGoalUpdateCommand(targetAmount, laborIntensity);
    }
}
