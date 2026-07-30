package com.ntropy.account.integration.codef;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.ntropy.account.config.CodefProperties;
import com.ntropy.account.config.CodefServiceType;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.service.AccountCollectionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 선택한 은행의 개인 데모 계정 등록/추가 -> connectedId MySQL 저장/재조회
 * -> 개인 보유계좌 조회·저장 -> 수시입출 계좌 거래내역 조회·저장까지 수동 검증.
 * 실제 로그인 ID, 비밀번호, 생년월일은 환경변수로만 받아 요청 시점에 사용하며 소스·DB·로그에 저장하지 않는다.
 * 은행별 비밀번호 오류 제한이 있으므로 자격증명을 확인한 뒤 기관당 한 번만 실행한다.
 */
class CodefConnectionManualVerificationTest {

    private static final Long DEMO_USER_ID = 9_000_000_088L;

    // 대상 은행 중 가장 짧은 SC은행 3개월(회당) 제약도 만족하는 조회 기간
    private static final int TRANSACTION_LOOKBACK_DAYS = 90;

    @Test
    void registersSelectedPersonalDemoAccountAndPersistsAccountsAndTransactions() {
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

            AccountCollectionService service = ctx.getBean(AccountCollectionService.class);
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(TRANSACTION_LOOKBACK_DAYS);

            List<Account> savedAccounts = service.registerAndCollect(
                    DEMO_USER_ID, bank, loginId, loginPassword, birthDate, startDate, endDate
            );

            assertTrue(!savedAccounts.isEmpty(), "데모 개인 보유계좌 응답이 비어 있음");

            Set<AccountGroup> groups = new LinkedHashSet<>();
            for (Account account : savedAccounts) {
                groups.add(account.getAccountGroup());
            }
            System.out.println("CODEF_BANK=" + bank.getDisplayName()
                    + "(" + bank.getOrganizationCode() + ")");
            System.out.println("CODEF_SAVED_ACCOUNT_COUNT=" + savedAccounts.size());
            System.out.println("CODEF_ACCOUNT_GROUPS=" + String.join(
                    ",", groups.stream().map(Enum::name).sorted().toList()
            ));

            Account ordinaryDepositAccount = savedAccounts.stream()
                    .filter(a -> a.getAccountGroup() == AccountGroup.DEPOSIT_TRUST)
                    .filter(a -> "10".equals(a.getDepositTypeCode()) || "11".equals(a.getDepositTypeCode()))
                    .findFirst()
                    .orElse(null);

            if (ordinaryDepositAccount == null || bank == PersonalBank.SC_BANK) {
                System.out.println("CODEF_TRANSACTION_COUNT=skipped (수시입출 계좌 없음 또는 SC은행 제외)");
            } else {
                DataSource dataSource = ctx.getBean(DataSource.class);
                long transactionCount = countAccountTransactions(dataSource, ordinaryDepositAccount.getId());
                System.out.println("CODEF_TRANSACTION_COUNT=" + transactionCount);
            }
        }
    }

    private static long countAccountTransactions(DataSource dataSource, Long accountId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM ACCOUNT_TRANSACTION WHERE account_id = ?"
             )) {
            statement.setLong(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ACCOUNT_TRANSACTION 개수 조회 실패", e);
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
