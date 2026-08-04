package com.ntropy.bff.dto.defense.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class DefenseModeResponse {
    private Long defenseId;
    private Long userId;
    private String causeCode;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate unavailableStartDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate expectedReturnDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate returnDate;
    private Long availableAssetsSnapshot;
    private Long averageMonthlyExpense;
    private Long dailyExpense;
    @JsonProperty("dDay")
    private Integer dDay;
    private String calculationStatus;
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    private List<DefenseChecklistResponse> checklist;
    private FixedExpenseCheckResponse fixedExpenseCheck;
    private ExpectedIncomeLossResponse expectedIncomeLoss;
    private GrowthModeResponse growthMode;

    public static DefenseModeResponse from(DefenseModeSummary summary) {
        return new DefenseModeResponse(
                summary.getDefenseId(), summary.getUserId(), summary.getCauseCode(),
                summary.getUnavailableStartDate(), summary.getExpectedReturnDate(), summary.getReturnDate(),
                summary.getAvailableAssetsSnapshot(), summary.getAverageMonthlyExpense(),
                summary.getDailyExpense(), summary.getDDay(), summary.getCalculationStatus(),
                summary.getStatus(), summary.getCreatedAt(),
                summary.getChecklist().stream().map(DefenseChecklistResponse::from).collect(Collectors.toList()),
                FixedExpenseCheckResponse.from(summary.getFixedExpenseCheck()),
                ExpectedIncomeLossResponse.from(summary.getExpectedIncomeLoss()),
                GrowthModeResponse.from(summary.getGrowthMode()));
    }
}
