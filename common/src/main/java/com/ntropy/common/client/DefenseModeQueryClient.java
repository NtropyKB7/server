package com.ntropy.common.client;

import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import com.ntropy.common.dto.defense.summary.DefenseCauseSummary;
import com.ntropy.common.dto.defense.summary.DefenseCalendarPeriodSummary;

import java.time.LocalDate;
import java.util.List;

public interface DefenseModeQueryClient {
    List<DefenseCauseSummary> getCauses();
    DefenseModeSummary getCurrent(Long userId);
    List<DefenseCalendarPeriodSummary> getCalendarPeriods(Long userId, LocalDate from, LocalDate to);
}
