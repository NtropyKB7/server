package com.ntropy.bff.dto.defense.response;

import com.ntropy.common.dto.defense.summary.DefenseCauseSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DefenseCausesResponse {
    private List<DefenseCauseSummary> causes;
}
