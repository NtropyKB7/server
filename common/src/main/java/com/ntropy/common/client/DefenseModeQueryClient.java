package com.ntropy.common.client;

import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import com.ntropy.common.dto.defense.summary.DefenseCauseSummary;

import java.util.List;

public interface DefenseModeQueryClient {
    List<DefenseCauseSummary> getCauses();
    DefenseModeSummary getCurrent(Long userId);
}
