package com.ntropy.account.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class AccountMapperContractTest {

    @Test
    void virtualDatasetExistenceCheckIncludesInactiveAccounts() throws IOException {
        String mapper = readResource("mapper/account/AccountMapper.xml");
        String statementStart = "<select id=\"existsAnyByUserIdAndProvider\"";
        int start = mapper.indexOf(statementStart);
        assertTrue(start >= 0, "가상계좌 존재 확인 쿼리가 필요합니다");

        int end = mapper.indexOf("</select>", start);
        assertTrue(end > start, "가상계좌 존재 확인 쿼리가 닫혀 있어야 합니다");
        String statement = mapper.substring(start, end).toLowerCase(Locale.ROOT);

        assertTrue(statement.contains("select exists"));
        assertTrue(statement.contains("connection_row.provider = #{provider}"));
        assertFalse(statement.contains("status"), "비활성 계좌도 기존 가상 데이터셋으로 판단해야 합니다");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = AccountMapperContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path + "가 테스트 classpath에 있어야 합니다");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
