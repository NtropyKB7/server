package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FinancialCommitmentMapperContractTest {

    @Test
    void savingQueryScopesByOwnerActiveKrwAndDepositTypeCode() throws IOException {
        String query = selectBody(readMapper(), "findSavingCommitmentCandidates");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code = '12'"));
        assertTrue(query.contains("transaction_category = 'INSTALLMENT'"));
        assertTrue(query.contains("installment_row.account_id = account_row.account_id"));
        assertTrue(query.contains("ORDER BY installment_row.tran_date DESC"));
        assertTrue(query.contains("LIMIT 1"));
        assertFalse(query.contains("ROW_NUMBER()"));
    }

    @Test
    void loanQueryScopesByGroupAndExcludesNonRepaymentKeywords() throws IOException {
        String query = selectBody(readMapper(), "findLoanCommitmentCandidates");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code = '40'"));
        assertTrue(query.contains("account_row.account_group = 'LOAN'"));
        assertTrue(query.contains("latest_tx.loan_principal_amount AS expectedPrincipalAmount"));
        assertTrue(query.contains("latest_tx.loan_interest_amount AS expectedInterestAmount"));
        assertTrue(query.contains("transaction_category = 'LOAN'"));
        assertTrue(query.contains("loan_row.out_amount > 0"));
        assertTrue(query.contains("loan_transaction_type_name IS NULL"));
        assertTrue(query.contains("NOT LIKE '%신규%'"));
        assertTrue(query.contains("NOT LIKE '%실행%'"));
        assertTrue(query.contains("NOT LIKE '%대출실행%'"));
        assertTrue(query.contains("NOT LIKE '%증액%'"));
        assertTrue(query.contains("loan_row.account_id = account_row.account_id"));
        assertTrue(query.contains("ORDER BY loan_row.tran_date DESC"));
        assertTrue(query.contains("LIMIT 1"));
        assertFalse(query.contains("ROW_NUMBER()"));
    }

    @Test
    void insuranceQueryScopesByOwnerAndObservationWindowOnOrdinaryOutflows() throws IOException {
        String query = selectBody(readMapper(), "findInsuranceOutflowCandidates");

        assertTrue(query.contains("account_row.user_id = #{userId}"));
        assertTrue(query.contains("account_row.status = 'ACTIVE'"));
        assertTrue(query.contains("account_row.currency_code = 'KRW'"));
        assertTrue(query.contains("account_row.deposit_type_code = '11'"));
        assertTrue(query.contains("transaction_category = 'ORDINARY'"));
        assertTrue(query.contains("transaction_row.out_amount > 0"));
        assertTrue(query.contains(
                "transaction_row.tran_date BETWEEN #{observationStartDate} AND #{observationEndDate}"));
        assertFalse(query.contains("ROW_NUMBER()"));
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/FinancialCommitmentMapper.xml";
        try (InputStream input = FinancialCommitmentMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
