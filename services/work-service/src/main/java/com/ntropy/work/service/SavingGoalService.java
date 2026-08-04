package com.ntropy.work.service;

import org.springframework.stereotype.Service;

import com.ntropy.work.domain.entity.SavingGoal;
import com.ntropy.work.mapper.SavingGoalMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavingGoalService {

    private static final long MIN_LABOR_INTENSITY = 1;
    private static final long MAX_LABOR_INTENSITY = 5;

    private final SavingGoalMapper savingGoalMapper;

    /**
     * 월별 저축 목표 등록. 같은 유저가 같은 달에 중복 등록할 수 없다.
     */
    public SavingGoal registerSavingGoal(SavingGoal savingGoal) {
        validate(savingGoal);

        SavingGoal existing = savingGoalMapper.findByUserIdAndTargetMonth(
                savingGoal.getUserId(), savingGoal.getTargetMonth());
        if (existing != null) {
            throw new IllegalArgumentException(
                    "이미 등록된 저축 목표입니다. userId=" + savingGoal.getUserId()
                            + ", targetMonth=" + savingGoal.getTargetMonth());
        }

        savingGoalMapper.insert(savingGoal);
        return savingGoal;
    }

    private void validate(SavingGoal savingGoal) {
        if (savingGoal.getTargetAmount() == null || savingGoal.getTargetAmount() <= 0) {
            throw new IllegalArgumentException("target_amount는 0보다 커야 합니다.");
        }
        Long laborIntensity = savingGoal.getLaborIntensity();
        if (laborIntensity == null || laborIntensity < MIN_LABOR_INTENSITY || laborIntensity > MAX_LABOR_INTENSITY) {
            throw new IllegalArgumentException("labor_intensity는 " + MIN_LABOR_INTENSITY
                    + "~" + MAX_LABOR_INTENSITY + " 사이여야 합니다.");
        }
    }
}
