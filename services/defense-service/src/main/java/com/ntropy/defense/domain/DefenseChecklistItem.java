package com.ntropy.defense.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DefenseChecklistItem {
    SECURE_SAFETY("안전 확보", "긴급한 위험이 있다면 안전한 장소로 이동하고 119 또는 관계기관에 도움을 요청하세요."),
    RECORD_INCIDENT("사고 기록 보관", "발생 시각과 장소를 기록하고 현장·파손 상태 및 관련 화면을 보관하세요."),
    VISIT_MEDICAL_PROVIDER("진료 및 증빙 보관", "의료기관 진료 후 진료기록과 영수증을 보관하세요. 필요한 경우 <a href=\"https://www.e-gen.or.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">가까운 의료기관 찾기 ↗</a>를 이용하세요."),
    CHECK_WORK_ACCIDENT_COVERAGE("업무상 재해 적용 가능성 확인", "업무 또는 업무 관련 이동 중 발생했다면 <a href=\"https://webzine.comwel.or.kr/vol152/sub02.html\" target=\"_blank\" rel=\"noopener noreferrer\">노무제공자 산재보험 안내 ↗</a>를 확인하세요."),
    FOLLOW_TREATMENT_PLAN("치료 계획 확인", "예상 치료기간과 근무 가능 시점을 의료진과 확인하세요."),
    REVIEW_HEALTH_COVERAGE("보장 내역 확인", "가입한 보험의 청구 가능 여부와 필요 서류를 확인하고, <a href=\"https://www.nhis.or.kr/static/html/wbda/g/wbdag0103.html\" target=\"_blank\" rel=\"noopener noreferrer\">건강보험·실손보험 보장 안내 ↗</a>를 참고하세요."),
    SAVE_RESTRICTION_NOTICE("제한 통지 보관", "플랫폼이 보낸 이용 제한 사유와 통지 화면을 캡처해 보관하세요."),
    FILE_PLATFORM_APPEAL("플랫폼 이의제기 확인", "플랫폼 고객센터에서 이의제기 절차와 제출 기한을 확인하세요. 추가 상담이 필요하면 <a href=\"https://www.work24.go.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">고용·노동 지원 안내 ↗</a>를 확인하세요."),
    SAVE_WORK_HISTORY("업무 이력 보관", "최근 배차·정산·평가 내역 등 정상적인 업무 수행을 보여주는 자료를 보관하세요."),
    ASSESS_REPAIR("수리 범위 확인", "차량 또는 장비의 고장 원인, 수리비와 예상 수리기간을 확인하세요."),
    CHECK_RENTAL_OPTION("대체 장비 확인", "수리기간 동안 이용할 수 있는 렌트·대체 장비와 비용을 확인하세요."),
    CHECK_DAMAGE_COVERAGE("손해 보장 확인", "자동차보험·장비보험·제조사 보증 등 적용 가능한 보장을 확인하세요. 수리비 또는 품질보증 관련 분쟁은 <a href=\"https://www.consumer.go.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">소비자 상담·피해구제 ↗</a>를 이용하세요."),
    ESTIMATE_CARE_PERIOD("돌봄 기간 확인", "집중 돌봄이 필요한 예상 기간과 다른 가족의 지원 가능 여부를 확인하세요."),
    CHECK_CARE_SUPPORT("돌봄 지원 확인", "이용 가능한 지역 돌봄서비스와 가족 지원제도를 확인하고, 필요한 경우 <a href=\"https://www.idolbom.go.kr/front/\" target=\"_blank\" rel=\"noopener noreferrer\">아이돌봄서비스 확인·신청 ↗</a>을 이용하세요."),
    RECORD_OTHER_CAUSE("상황 기록", "근무가 어려워진 사유와 시작일, 관련 증빙자료를 정리해 두세요."),
    REVIEW_RETURN_DATE("복귀일 재확인", "예상 복귀일이 현실적인지 확인하고 변경이 필요하면 방어모드 기간을 조정하세요.");

    private final String title;
    private final String description;
}
