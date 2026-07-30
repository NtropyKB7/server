package com.ntropy.payment.domain;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 구독 플랜 코드.
 * DB(SUBSCRIPTION.plan_code, PAYMENT.plan_code)에는 이 enum의 name()이 그대로 저장된다.
 *
 * featureLabels는 GET /api/subscriptions/plans 응답에 그대로 노출되는 한국어 표시 문구다.
 * PRO는 Basic 기능을 다시 나열하지 않고 "Basic 모든 기능"으로 묶어서 보여준다
 * (API 명세 샘플과 1:1로 맞춘 것이라, 아래 features(Feature enum 조합)와는 별도로 관리한다).
 *
 * features는 다른 도메인(예: defense-service)이 "이 플랜이 DEFENSE_MODE를 지원하는가"를
 * 물을 때 쓰는 내부 권한 판단용 플래그다 (Issue #3에서 SubscriptionQueryClient가 활용).
 */
@Getter
public enum PlanCode {

    BASIC(
            "Basic",
            0,
            List.of("대시보드", "캘린더", "소득 분석"),
            EnumSet.of(com.ntropy.common.domain.Feature.DASHBOARD, com.ntropy.common.domain.Feature.CALENDAR, com.ntropy.common.domain.Feature.INCOME_ANALYSIS)
    ),

    PRO(
            "Pro",
            4_900,
            List.of("Basic 모든 기능", "방어모드", "AI 리포트 제공"),
            EnumSet.of(com.ntropy.common.domain.Feature.DASHBOARD, com.ntropy.common.domain.Feature.CALENDAR, com.ntropy.common.domain.Feature.INCOME_ANALYSIS,
                    com.ntropy.common.domain.Feature.DEFENSE_MODE, com.ntropy.common.domain.Feature.AI_REPORT)
    );

    private final String displayName;
    private final int monthlyPrice;
    private final List<String> featureLabels;
    private final Set<com.ntropy.common.domain.Feature> features;

    PlanCode(String displayName, int monthlyPrice, List<String> featureLabels, Set<com.ntropy.common.domain.Feature> features) {
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.featureLabels = featureLabels;
        this.features = features;
    }

    public boolean supports(com.ntropy.common.domain.Feature feature) {
        return features.contains(feature);
    }
}