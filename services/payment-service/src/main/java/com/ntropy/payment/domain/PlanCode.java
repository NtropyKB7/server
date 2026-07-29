package com.ntropy.payment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PlanCode {

    BASIC(
            "Basic",
            0,
            "대시보드, 캘린더, 소득 분석",
            EnumSet.of(Feature.DASHBOARD, Feature.CALENDAR, Feature.INCOME_ANALYSIS)
    ),

    PRO(
            "Pro",
            4_900,
            "Basic 모든 기능, 방어모드, AI 리포트 제공",
            EnumSet.of(Feature.DASHBOARD, Feature.CALENDAR, Feature.INCOME_ANALYSIS,
                    Feature.DEFENSE_MODE, Feature.AI_REPORT)
    );

    private final String displayName;
    private final int monthlyPrice;
    private final String description;
    private final Set<Feature> features;

    PlanCode(String displayName, int monthlyPrice, String description, Set<Feature> features) {
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.description = description;
        this.features = features;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMonthlyPrice() {
        return monthlyPrice;
    }

    public String getDescription() {
        return description;
    }

    public Set<Feature> getFeatures() {
        return features;
    }

    public boolean supports(Feature feature) {
        return features.contains(feature);
    }
}