package com.ntropy.payment.dto;

import com.ntropy.payment.domain.PlanCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanInfo {

    private String planCode;
    private String displayName;
    private int monthlyPrice;
    private String description;
    private List<String> features;

    public static PlanInfo from(PlanCode planCode) {
        List<String> featureNames = planCode.getFeatures().stream()
                .map(Enum::name)
                .collect(Collectors.toList());
        return new PlanInfo(
                planCode.name(),
                planCode.getDisplayName(),
                planCode.getMonthlyPrice(),
                planCode.getDescription(),
                featureNames
        );
    }
}