package com.ntropy.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class InsuranceCompanyTest {

    @Test
    void matchesSamsungLifeAliases() {
        assertEquals(Optional.of("삼성생명"), InsuranceCompany.matchStandardName("삼성생명보험"));
        assertEquals(Optional.of("삼성생명"), InsuranceCompany.matchStandardName("삼성생명"));
    }

    @Test
    void matchesDbInsuranceAliasesIncludingCorporateMark() {
        assertEquals(Optional.of("DB손보"), InsuranceCompany.matchStandardName("DB손해보험㈜"));
        assertEquals(Optional.of("DB손보"), InsuranceCompany.matchStandardName("DB손해보험"));
        assertEquals(Optional.of("DB손보"), InsuranceCompany.matchStandardName("DB손보"));
    }

    @Test
    void matchesKbInsuranceAliases() {
        assertEquals(Optional.of("KB손보"), InsuranceCompany.matchStandardName("KB손해보험"));
        assertEquals(Optional.of("KB손보"), InsuranceCompany.matchStandardName("KB손보"));
    }

    @Test
    void matchesNationalHealthInsuranceAliases() {
        assertEquals(Optional.of("국민건강보험"), InsuranceCompany.matchStandardName("국민건강보험공단"));
        assertEquals(Optional.of("국민건강보험"), InsuranceCompany.matchStandardName("국민건강보험"));
        assertEquals(Optional.of("국민건강보험"), InsuranceCompany.matchStandardName("건강보험"));
    }

    @Test
    void matchesNationalPensionAliases() {
        assertEquals(Optional.of("국민연금"), InsuranceCompany.matchStandardName("국민연금공단"));
        assertEquals(Optional.of("국민연금"), InsuranceCompany.matchStandardName("국민연금"));
    }

    @Test
    void ignoresWhitespaceAndCaseWhenMatching() {
        assertEquals(Optional.of("DB손보"), InsuranceCompany.matchStandardName("  db 손해 보험 "));
        assertEquals(Optional.of("KB손보"), InsuranceCompany.matchStandardName("kb손보 자동이체"));
    }

    @Test
    void matchesAliasEmbeddedInLongerTransactionDescription() {
        Optional<String> match = InsuranceCompany.matchStandardName("삼성생명보험|정기납|123-456|강남지점");
        assertEquals(Optional.of("삼성생명"), match);
    }

    @Test
    void returnsEmptyForUnrelatedRecurringPayments() {
        assertTrue(InsuranceCompany.matchStandardName("체크카드승인").isEmpty());
        assertTrue(InsuranceCompany.matchStandardName("FBS 출금").isEmpty());
    }

    @Test
    void returnsEmptyForNullOrBlankDescription() {
        assertTrue(InsuranceCompany.matchStandardName(null).isEmpty());
        assertTrue(InsuranceCompany.matchStandardName("   ").isEmpty());
    }
}
