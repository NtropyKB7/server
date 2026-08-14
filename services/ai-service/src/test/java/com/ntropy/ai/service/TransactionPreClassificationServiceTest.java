package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ntropy.common.dto.account.DailyClassificationTargetTransaction;
import com.ntropy.common.dto.account.TransactionAnalysisSaveItem;

class TransactionPreClassificationServiceTest {

    private final TransactionPreClassificationService service =
            new TransactionPreClassificationService();

    @Test
    void normalLoanRepaymentIsFixedFinanceConsumption() {
        TransactionAnalysisSaveItem result = service.classify(
                target(
                        "LOAN",
                        null,
                        null
                )
        ).orElseThrow();

        assertTrue(result.getIsConsumption());
        assertEquals("FINANCE", result.getCategory());
        assertEquals("FIXED", result.getExpenseType());
    }

    @Test
    void loanDisbursementIsNonConsumption() {
        TransactionAnalysisSaveItem result = service.classify(
                target(
                        "LOAN",
                        "대출 실행",
                        null
                )
        ).orElseThrow();

        assertFalse(result.getIsConsumption());
        assertNull(result.getCategory());
        assertNull(result.getExpenseType());
    }

    @Test
    void installmentPaymentIsFixedFinanceConsumption() {
        TransactionAnalysisSaveItem result = service.classify(
                target(
                        "INSTALLMENT",
                        null,
                        null
                )
        ).orElseThrow();

        assertTrue(result.getIsConsumption());
        assertEquals("FINANCE", result.getCategory());
        assertEquals("FIXED", result.getExpenseType());
    }

    @Test
    void clearFinancialTransferIsNonConsumption() {
        TransactionAnalysisSaveItem result = service.classify(
                target(
                        "ORDINARY",
                        null,
                        "정기적금"
                )
        ).orElseThrow();

        assertFalse(result.getIsConsumption());
        assertNull(result.getCategory());
        assertNull(result.getExpenseType());
    }

    @Test
    void genericTransactionChannelIsNotEnoughForNonConsumption() {
        Optional<TransactionAnalysisSaveItem> result = service.classify(
                target(
                        "ORDINARY",
                        null,
                        "자동이체"
                )
        );

        /*
         * Optional.empty()는 Spring에서 소비 여부를 확정하지 않고
         * FastAPI로 전달해야 한다는 의미입니다.
         */
        assertTrue(result.isEmpty());
    }

    @Test
    void cardBillIsVariableEtcConsumption() {
        assertConsumptionDecision(
                "카드이용대금",
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void cashWithdrawalIsVariableEtcConsumption() {
        assertConsumptionDecision(
                "ATM출금",
                "ETC",
                "VARIABLE"
        );
    }

    @Test
    void insuranceIsFixedInsuranceConsumption() {
        assertConsumptionDecision(
                "삼성생명 실손보험",
                "INSURANCE",
                "FIXED"
        );
    }

    private void assertConsumptionDecision(
            String description,
            String expectedCategory,
            String expectedExpenseType
    ) {
        TransactionAnalysisSaveItem result = service.classify(
                target(
                        "ORDINARY",
                        null,
                        description
                )
        ).orElseThrow();

        assertTrue(result.getIsConsumption());
        assertEquals(
                expectedCategory,
                result.getCategory()
        );
        assertEquals(
                expectedExpenseType,
                result.getExpenseType()
        );
    }

    private DailyClassificationTargetTransaction target(
            String transactionCategory,
            String loanTransactionTypeName,
            String desc3
    ) {
        return new DailyClassificationTargetTransaction(
                1L,
                10L,
                transactionCategory,
                10_000L,
                10_000L,
                "0004",
                loanTransactionTypeName,
                null,
                null,
                desc3,
                null
        );
    }
}