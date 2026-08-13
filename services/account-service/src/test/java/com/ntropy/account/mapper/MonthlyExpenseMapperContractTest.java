package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MonthlyExpenseMapperContractTest {

    @Test
    void totalExpenseQueryDoesNotFilterByCurrentAccountStatus() throws IOException {
        String query = selectBody(readMapper(), "findTotalExpense");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("<include refid=\"consumptionFilter\"/>"));
        assertTrue(query.contains("<include refid=\"loanAwareAmount\"/>"));
    }

    @Test
    void categoryExpensesQueryDoesNotFilterByCurrentAccountStatus() throws IOException {
        String query = selectBody(readMapper(), "findCategoryExpenses");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("<include refid=\"consumptionFilter\"/>"));
        assertTrue(query.contains("<include refid=\"loanAwareCategory\"/>"));
        assertTrue(query.contains("<include refid=\"loanAwareAmount\"/>"));
    }

    @Test
    void fixedExpenseQueryDoesNotFilterByCurrentAccountStatusAndScopesToFixedType() throws IOException {
        String query = selectBody(readMapper(), "findFixedExpense");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("<include refid=\"fixedConsumptionFilter\"/>"));
        assertTrue(query.contains("<include refid=\"loanAwareAmount\"/>"));
    }

    /**
     * 이슈 #143: 세 쿼리 모두 같은 loanAwareAmount fragment로 금액을 계산해야 계산식이 갈라지지 않는다.
     * TXN_ANALYSIS 행이 없는 LOAN 거래도 걸려야 하므로 INNER JOIN이 아닌 LEFT JOIN을 써야 한다.
     */
    @Test
    void allThreeQueriesShareTheSameLoanAmountAndUseLeftJoinForOptionalAnalysis() throws IOException {
        String mapper = readMapper();
        for (String selectId : new String[] {"findTotalExpense", "findCategoryExpenses", "findFixedExpense"}) {
            String query = selectBody(mapper, selectId);
            assertTrue(query.contains("<include refid=\"loanAwareAmount\"/>"),
                    selectId + "는 공통 loanAwareAmount fragment를 재사용해야 합니다");
            assertTrue(query.contains("LEFT JOIN TXN_ANALYSIS analysis_row"),
                    selectId + "는 TXN_ANALYSIS가 없는 LOAN 거래도 포함하도록 LEFT JOIN을 써야 합니다");
        }
    }

    @Test
    void loanAwareAmountExcludesPrincipalAndClampsInterestAtZero() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "loanAwareAmount");

        assertTrue(fragment.contains("transaction_category = 'LOAN'"));
        assertTrue(fragment.contains("loan_interest_amount"));
        assertTrue(fragment.contains("GREATEST("),
                "음수 loan_interest_amount가 총소비를 깎지 않도록 0으로 클램프해야 합니다");
        assertFalse(fragment.contains("loan_principal_amount"),
                "LOAN 원금(loan_principal_amount)은 소비 금액 계산에 쓰지 않아야 합니다");
    }

    @Test
    void loanAwareCategoryFixesLoanInterestToFinance() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "loanAwareCategory");

        assertTrue(fragment.contains("transaction_category = 'LOAN'"));
        assertTrue(fragment.contains("'FINANCE'"));
        assertTrue(fragment.contains("analysis_row.category"));
    }

    @Test
    void loanInterestConsumptionFilterOnlyIncludesPositiveInterestRepayments() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "loanInterestConsumptionFilter");

        assertTrue(fragment.contains("transaction_row.transaction_category = 'LOAN'"));
        assertTrue(fragment.contains("transaction_row.out_amount &gt; 0"));
        assertTrue(fragment.contains("transaction_row.loan_interest_amount &gt; 0"));
        assertTrue(fragment.contains("loan_transaction_type_name IS NULL"));
        assertTrue(fragment.contains("NOT LIKE '%신규%'"));
        assertTrue(fragment.contains("NOT LIKE '%실행%'"));
        assertTrue(fragment.contains("NOT LIKE '%대출실행%'"));
        assertTrue(fragment.contains("NOT LIKE '%증액%'"));
    }

    @Test
    void consumptionFilterIncludesOnlyEligibleLoanOrClassifiedNonLoan() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "consumptionFilter");

        assertTrue(fragment.contains("<include refid=\"loanInterestConsumptionFilter\"/>"));
        assertTrue(fragment.contains("transaction_row.transaction_category &lt;&gt; 'LOAN'"));
        assertTrue(fragment.contains("analysis_row.is_consumption = TRUE"));
    }

    @Test
    void fixedConsumptionFilterIncludesOnlyEligibleLoanAndKeepsFixedTypeForNonLoan() throws IOException {
        String fragment = sqlFragmentBody(readMapper(), "fixedConsumptionFilter");

        assertTrue(fragment.contains("<include refid=\"loanInterestConsumptionFilter\"/>"));
        assertTrue(fragment.contains("transaction_row.transaction_category &lt;&gt; 'LOAN'"));
        assertTrue(fragment.contains("analysis_row.is_consumption = TRUE"));
        assertTrue(fragment.contains("analysis_row.expense_type = 'FIXED'"));
    }

    private static String sqlFragmentBody(String mapper, String sqlId) {
        int start = mapper.indexOf("<sql id=\"" + sqlId + "\"");
        int end = mapper.indexOf("</sql>", start);
        return mapper.substring(start, end);
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/MonthlyExpenseMapper.xml";
        try (InputStream input = MonthlyExpenseMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
