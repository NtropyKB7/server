package com.ntropy.defense.domain;

import lombok.Getter;

@Getter
public enum DefenseCause {
    ACCIDENT_INJURY("ACCIDENT", "사고", null),
    ILLNESS("HEALTH", "질병", null),
    PLATFORM_RESTRICTION("PLATFORM", "계정정지", null),
    EQUIPMENT_FAILURE("EQUIPMENT", "장비고장", null),
    FAMILY_CARE_CRISIS("FAMILY", "육아·돌봄", null),
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
