package com.ntropy.account.mapper;

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
        assertTrue(query.contains("analysis_row.is_consumption = TRUE"));
    }

    @Test
    void categoryExpensesQueryDoesNotFilterByCurrentAccountStatus() throws IOException {
        String query = selectBody(readMapper(), "findCategoryExpenses");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("analysis_row.is_consumption = TRUE"));
    }

    @Test
    void fixedExpenseQueryDoesNotFilterByCurrentAccountStatusAndScopesToFixedType() throws IOException {
        String query = selectBody(readMapper(), "findFixedExpense");

        assertTrue(!query.contains("status = 'ACTIVE'"));
        assertTrue(query.contains("analysis_row.is_consumption = TRUE"));
        assertTrue(query.contains("analysis_row.expense_type = 'FIXED'"));
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
