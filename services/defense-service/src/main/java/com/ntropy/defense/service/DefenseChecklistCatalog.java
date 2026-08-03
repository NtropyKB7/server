package com.ntropy.defense.service;

import com.ntropy.defense.domain.DefenseCause;
import com.ntropy.defense.domain.DefenseChecklistItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.ntropy.defense.domain.DefenseChecklistItem.*;

public final class DefenseChecklistCatalog {
    private static final Map<DefenseCause, List<DefenseChecklistItem>> ITEMS = new EnumMap<>(DefenseCause.class);

    static {
        ITEMS.put(DefenseCause.ACCIDENT_INJURY, Arrays.asList(
                SECURE_SAFETY, RECORD_INCIDENT, VISIT_MEDICAL_PROVIDER,
                CHECK_WORK_ACCIDENT_COVERAGE, REVIEW_RETURN_DATE));
        ITEMS.put(DefenseCause.ILLNESS, Arrays.asList(
                VISIT_MEDICAL_PROVIDER, FOLLOW_TREATMENT_PLAN, REVIEW_HEALTH_COVERAGE, REVIEW_RETURN_DATE));
        ITEMS.put(DefenseCause.PLATFORM_RESTRICTION, Arrays.asList(
                SAVE_RESTRICTION_NOTICE, FILE_PLATFORM_APPEAL, SAVE_WORK_HISTORY, REVIEW_RETURN_DATE));
        ITEMS.put(DefenseCause.EQUIPMENT_FAILURE, Arrays.asList(
                ASSESS_REPAIR, CHECK_RENTAL_OPTION, CHECK_DAMAGE_COVERAGE, REVIEW_RETURN_DATE));
        ITEMS.put(DefenseCause.FAMILY_CARE_CRISIS, Arrays.asList(
                ESTIMATE_CARE_PERIOD, CHECK_CARE_SUPPORT, REVIEW_RETURN_DATE));
        ITEMS.put(DefenseCause.OTHER, Arrays.asList(RECORD_OTHER_CAUSE, REVIEW_RETURN_DATE));
    }

    private DefenseChecklistCatalog() {
    }

    public static List<DefenseChecklistItem> findBy(DefenseCause cause) {
        return Collections.unmodifiableList(ITEMS.getOrDefault(cause, Collections.emptyList()));
    }
}
