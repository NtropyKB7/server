package com.ntropy.account.domain;

/** 은행별 CODEF 설명 필드에서 입금 거래의 상대방명을 선택한다. */
public final class IncomingCounterpartyNameExtractor {

    private IncomingCounterpartyNameExtractor() {
    }

    public static String extract(
            String organizationCode,
            String desc1,
            String desc2,
            String desc3,
            String desc4
    ) {
        PersonalBank bank = PersonalBank.fromOrganizationCode(organizationCode);
        String counterpartyName = switch (bank) {
            case IBK_INDUSTRIAL_BANK -> desc1;
            case KB_KOOKMIN_BANK, NH_BANK, SC_BANK, JEONBUK_BANK, KYONGNAM_BANK,
                    SAEMAUL_GEUMGO, KEB_HANA_BANK, SHINHAN_BANK -> desc3;
        };
        return counterpartyName == null || counterpartyName.isBlank() ? null : counterpartyName;
    }
}
