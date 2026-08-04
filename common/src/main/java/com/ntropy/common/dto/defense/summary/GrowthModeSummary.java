package com.ntropy.common.dto.defense.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GrowthModeSummary {
    private Boolean isPaused;
    private String integrationStatus;
    private String message;
}
