package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FinancialDataQueryMapperContractTest {

    @Test
    void scopesAccountListAndDetailByUserId() throws IOException {
        String mapper = readMapper();

        assertTrue(mapper.contains("WHERE account_row.user_id = #{userId}"));
        assertTrue(mapper.contains("WHERE account_row.account_id = #{accountId}"));
        assertTrue(mapper.contains("AND account_row.user_id = #{userId}"));
        assertTrue(mapper.contains("AND account_row.status = 'ACTIVE'"));
    }

    @Test
    void verifiesTransactionOwnershipBeforePagedQuery() throws IOException {
        String countQuery = selectBody(readMapper(), "countTransactionsByAccountIdAndUserId");
        String pageQuery = selectBody(readMapper(), "findTransactionsByAccountIdAndUserId");

        assertTrue(countQuery.contains("FROM ACCOUNT account_row"));
        assertTrue(countQuery.contains("LEFT JOIN ACCOUNT_TRANSACTION transaction_row"));
        assertTrue(countQuery.contains("COUNT(transaction_row.account_transaction_id)"));
        assertTrue(countQuery.contains("account_row.account_id = #{accountId}"));
        assertTrue(countQuery.contains("account_row.user_id = #{userId}"));
        assertTrue(pageQuery.contains("INNER JOIN ACCOUNT_TRANSACTION transaction_row"));
        assertTrue(pageQuery.contains("ORDER BY transaction_row.tran_date DESC"));
        assertTrue(pageQuery.contains("LIMIT #{limit} OFFSET #{offset}"));
        assertFalse(pageQuery.contains("EXISTS"));
    }

    private static String selectBody(String mapper, String selectId) {
        int start = mapper.indexOf("<select id=\"" + selectId + "\"");
        int end = mapper.indexOf("</select>", start);
        return mapper.substring(start, end);
    }

    private static String readMapper() throws IOException {
        String path = "mapper/account/FinancialDataQueryMapper.xml";
        try (InputStream input = FinancialDataQueryMapperContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("리소스를 찾을 수 없습니다: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
