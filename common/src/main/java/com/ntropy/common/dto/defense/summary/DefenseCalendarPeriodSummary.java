package com.ntropy.common.dto.defense.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseCalendarPeriodSummary {
    private Long defenseId;
    private LocalDate startDate;
    private LocalDate endDate;
}
