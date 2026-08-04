package com.ntropy.defense.domain;

import lombok.Getter;

@Getter
public enum DefenseCause {
    ACCIDENT_INJURY("ACCIDENT", "사고·부상", null),
    ILLNESS("HEALTH", "질병", null),
    PLATFORM_RESTRICTION("PLATFORM", "플랫폼 이용 제한", null),
    EQUIPMENT_FAILURE("EQUIPMENT", "차량·업무장비 문제", null),
    FAMILY_CARE_CRISIS("FAMILY", "가족·돌봄 위기", null),
    OTHER("ETC", "기타", "이 사유는 공통 체크리스트만 제공해요. 필요한 내용을 별도로 메모해두세요.");

    private final String causeGroup;
    private final String causeName;
    private final String guideMessage;

    DefenseCause(String causeGroup, String causeName, String guideMessage) {
        this.causeGroup = causeGroup;
        this.causeName = causeName;
        this.guideMessage = guideMessage;
    }
}
