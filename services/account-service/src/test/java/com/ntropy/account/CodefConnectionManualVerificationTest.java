package com.ntropy.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.service.CodefBankAccountClient;
import com.ntropy.account.service.CodefConnectionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 국민은행 개인 샌드박스 계정 등록 -> connectedId 발급 -> MySQL 저장/재조회
 * -> 개인 보유계좌 조회 수동 검증.
 * 샌드박스는 고정 응답을 사용하므로 실제 국민은행 로그인 정보는 사용하지 않는다.
 */
class CodefConnectionManualVerificationTest {

    private static final Long SANDBOX_USER_ID = 9_000_000_004L;

    @Test
    void registersKbPersonalSandboxAccountAndPersistsConnectedId() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_CODEF_ACCOUNT_TEST")),
                "외부 CODEF 샌드박스와 로컬 MySQL이 필요한 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            CodefConnectionService service = ctx.getBean(CodefConnectionService.class);

            CodefConnection saved = service.registerAndSave(
                    SANDBOX_USER_ID,
                    "0004",
                    "BK",
                    "P",
                    "sandbox-user",
                    "sandbox-password",
                    "19900101"
            );

            assertNotNull(saved);
            assertEquals(SANDBOX_USER_ID, saved.getUserId());
            assertNotNull(saved.getConnectedId());
            assertTrue(!saved.getConnectedId().isBlank());

            CodefBankAccountClient bankAccountClient = ctx.getBean(CodefBankAccountClient.class);
            JsonNode accountListResponse = bankAccountClient.getPersonalAccountList(
                    "0004",
                    saved.getConnectedId()
            );

            JsonNode accountGroups = accountListResponse.path("data");
            assertTrue(accountGroups.isObject());
            assertTrue(accountGroups.size() > 0, "샌드박스 개인 보유계좌 고정 응답이 비어 있음");

            List<String> groupFields = new ArrayList<>();
            accountGroups.fieldNames().forEachRemaining(groupFields::add);
            Collections.sort(groupFields);
            System.out.println("CODEF_ACCOUNT_LIST_GROUPS=" + String.join(",", groupFields));

            JsonNode depositAccounts = accountGroups.path("resDepositTrust");
            assertTrue(depositAccounts.isArray());
            assertTrue(depositAccounts.size() > 0, "샌드박스 예금/신탁 계좌 고정 응답이 비어 있음");

            List<String> accountFields = new ArrayList<>();
            depositAccounts.get(0).fieldNames().forEachRemaining(accountFields::add);
            Collections.sort(accountFields);
            System.out.println("CODEF_DEPOSIT_ACCOUNT_FIELDS=" + String.join(",", accountFields));
        }
    }

    @Configuration
    @ComponentScan(basePackages = "com.ntropy.account")
    @MapperScan("com.ntropy.account.mapper")
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
            config.setUsername("root");
            config.setPassword("root");
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return new HikariDataSource(config);
        }

        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(
                    new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")
            );
            return factoryBean;
        }
    }
}
