package com.ntropy.account.integration.codef;

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
import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.config.CodefServiceType;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.service.PersonalBankAccountService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 선택한 은행의 개인 데모 계정 등록/추가 -> connectedId MySQL 저장/재조회
 * -> 개인 보유계좌 조회 수동 검증.
 * 실제 로그인 ID, 비밀번호, 생년월일은 환경변수로만 받아 요청 시점에 사용하며 소스·DB·로그에 저장하지 않는다.
 * 은행별 비밀번호 오류 제한이 있으므로 자격증명을 확인한 뒤 기관당 한 번만 실행한다.
 */
class CodefConnectionManualVerificationTest {

    private static final Long DEMO_USER_ID = 9_000_000_088L;

    @Test
    void registersSelectedPersonalDemoAccountAndPersistsConnectedId() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_CODEF_ACCOUNT_TEST")),
                "외부 CODEF 데모와 로컬 MySQL이 필요한 수동 검증용 테스트"
        );

        PersonalBank bank = PersonalBank.fromOrganizationCode(
                requiredEnvironmentVariable("CODEF_DEMO_BANK_ORGANIZATION")
        );
        String loginId = requiredEnvironmentVariable("CODEF_DEMO_BANK_LOGIN_ID");
        String loginPassword = requiredEnvironmentVariable("CODEF_DEMO_BANK_LOGIN_PASSWORD");
        String birthDate = bank.isBirthDateRequired()
                ? requiredEnvironmentVariable("CODEF_DEMO_BANK_BIRTH_DATE")
                : null;

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            CodefProperties properties = ctx.getBean(CodefProperties.class);
            assertTrue(
                    CodefServiceType.DEMO == properties.getServiceType(),
                    "CODEF 서비스 타입이 DEMO여야 함"
            );

            PersonalBankAccountService service = ctx.getBean(PersonalBankAccountService.class);
            JsonNode accountListResponse = service.registerAndGetPersonalAccountList(
                    DEMO_USER_ID,
                    bank,
                    loginId,
                    loginPassword,
                    birthDate
            );

            JsonNode accountGroups = accountListResponse.path("data");
            assertTrue(accountGroups.isObject());
            assertTrue(accountGroups.size() > 0, "데모 개인 보유계좌 응답이 비어 있음");

            List<String> groupFields = new ArrayList<>();
            accountGroups.fieldNames().forEachRemaining(groupFields::add);
            Collections.sort(groupFields);
            System.out.println("CODEF_BANK=" + bank.getDisplayName()
                    + "(" + bank.getOrganizationCode() + ")");
            System.out.println("CODEF_ACCOUNT_LIST_GROUPS=" + String.join(",", groupFields));

            JsonNode depositAccounts = accountGroups.path("resDepositTrust");
            assertTrue(depositAccounts.isArray());
            assertTrue(depositAccounts.size() > 0, "데모 예금/신탁 계좌 응답이 비어 있음");

            List<String> accountFields = new ArrayList<>();
            depositAccounts.get(0).fieldNames().forEachRemaining(accountFields::add);
            Collections.sort(accountFields);
            System.out.println("CODEF_DEPOSIT_ACCOUNT_FIELDS=" + String.join(",", accountFields));
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다");
        }
        return value;
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
