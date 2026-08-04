package com.ntropy.defense.client;

import com.ntropy.common.client.DefenseModeCommandClient;
import com.ntropy.common.client.DefenseModeQueryClient;
import com.ntropy.common.dto.defense.command.DefenseModeEnterCommand;
import com.ntropy.common.dto.defense.command.DefenseModeReleaseCommand;
import com.ntropy.common.dto.defense.summary.DefenseChecklistSummary;
import com.ntropy.common.dto.defense.summary.DefenseCauseSummary;
import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import com.ntropy.common.dto.defense.summary.FixedExpenseCheckSummary;
import com.ntropy.common.dto.defense.summary.ExpectedIncomeLossSummary;
import com.ntropy.common.dto.defense.summary.GrowthModeSummary;
import com.ntropy.defense.domain.DefenseMode;
import com.ntropy.defense.domain.DefenseCause;
import com.ntropy.defense.service.DefenseChecklistCatalog;
import com.ntropy.defense.service.DefenseModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LocalDefenseModeClient implements DefenseModeCommandClient, DefenseModeQueryClient {
    private final DefenseModeService defenseModeService;

    @Override
    public List<DefenseCauseSummary> getCauses() {
        return java.util.Arrays.stream(DefenseCause.values())
                .map(cause -> new DefenseCauseSummary(
                        cause.name(), cause.getCauseGroup(), cause.getCauseName(),
                        DefenseChecklistCatalog.findBy(cause).stream()
                                .map(item -> item.getTitle())
                                .collect(Collectors.toList()),
                        cause.getGuideMessage()))
                .collect(Collectors.toList());
    }

    @Override
    public DefenseModeSummary enter(DefenseModeEnterCommand command) {
        return toSummary(defenseModeService.enter(command), null, null, null);
    }

    @Override
    public DefenseModeSummary getCurrent(Long userId) {
        DefenseMode defenseMode = defenseModeService.getCurrent(userId);
        FixedExpenseCheckSummary fixedExpenseCheck = defenseModeService.getFixedExpenseCheck(defenseMode);
        ExpectedIncomeLossSummary expectedIncomeLoss = defenseModeService.getExpectedIncomeLoss(defenseMode);
        GrowthModeSummary growthMode = new GrowthModeSummary(
                true,
                "DISPLAY_ONLY",
                "근무 추천과 자동 저축을 잠시 멈춘 상태입니다.");
        return toSummary(defenseMode, fixedExpenseCheck, expectedIncomeLoss, growthMode);
    }

    @Override
    public DefenseModeSummary release(Long defenseId, DefenseModeReleaseCommand command) {
        return toSummary(defenseModeService.release(defenseId, command), null, null, null);
    }

    private DefenseModeSummary toSummary(
            DefenseMode defenseMode,
            FixedExpenseCheckSummary fixedExpenseCheck,
            ExpectedIncomeLossSummary expectedIncomeLoss,
            GrowthModeSummary growthMode) {
        List<DefenseChecklistSummary> checklist = DefenseChecklistCatalog.findBy(defenseMode.getCauseCode()).stream()
                .map(item -> new DefenseChecklistSummary(item.name(), item.getTitle(), item.getDescription()))
                .collect(Collectors.toList());
        return new DefenseModeSummary(
                defenseMode.getDefenseId(), defenseMode.getUserId(), defenseMode.getCauseCode().name(),
                defenseMode.getUnavailableStartDate(), defenseMode.getExpectedReturnDate(),
                defenseMode.getReturnDate(), defenseMode.getAvailableAssetsSnapshot(),
                defenseMode.getAverageMonthlyExpense(), defenseMode.getDailyExpense(), defenseMode.getDDay(),
                defenseMode.getCalculationStatus() == null ? null : defenseMode.getCalculationStatus().name(),
                defenseMode.getStatus().name(),
                defenseMode.getCreatedAt(), checklist, fixedExpenseCheck, expectedIncomeLoss, growthMode);
    }
}
