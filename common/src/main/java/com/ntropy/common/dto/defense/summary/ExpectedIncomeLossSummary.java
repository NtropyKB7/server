package com.ntropy.common.dto.defense.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.ntropy.common.dto.work.summary.JobExpectedIncomeLossSummary;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedIncomeLossSummary {
    private Long totalAmount;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private String calculationStatus;
    private List<JobExpectedIncomeLossSummary> jobs;
}
