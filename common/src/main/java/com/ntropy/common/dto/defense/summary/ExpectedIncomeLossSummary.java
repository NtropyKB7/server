package com.ntropy.common.dto.defense.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedIncomeLossSummary {
    private Long amount;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private String calculationStatus;
}
