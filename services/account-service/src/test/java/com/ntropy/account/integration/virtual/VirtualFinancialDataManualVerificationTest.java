package com.ntropy.account.integration.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.VirtualConnectionService;
import com.ntropy.account.service.VirtualFinancialDataService;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** RUN_VIRTUAL_FINANCIAL_DATA_TEST=true일 때 로컬 MySQL에 FIN-005 목데이터를 적재한다. */
class VirtualFinancialDataManualVerificationTest {

    private static final long FIRST_USER_ID = 9_000_046_001L;
    private static final long LAST_USER_ID = 9_000_046_050L;

    @Test
    void generatesIdempotentVirtualFinancialDataset() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_VIRTUAL_FINANCIAL_DATA_TEST")),
                "로컬 MySQL에 FIN-005 목데이터를 적재하는 수동 검증 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            VirtualFinancialDataService service = context.getBean(VirtualFinancialDataService.class);
            GenerationSummary first = service.generate();
            GenerationSummary second = service.generate();
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            assertEquals(first, second);
            assertEquals(50, count(jdbc, """
                    SELECT COUNT(*) FROM CODEF_CONNECTION
                    WHERE provider = 'NTROPY' AND user_id BETWEEN ? AND ?
                    """));
            assertEquals(100, count(jdbc, """
                    SELECT COUNT(*) FROM ACCOUNT
                    WHERE user_id BETWEEN ? AND ?
                    """));
            assertEquals(15_000, count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    WHERE account_row.user_id BETWEEN ? AND ?
                    """));
            assertEquals(11, count(jdbc, """
                    SELECT COUNT(DISTINCT REPLACE(transaction_row.desc3, '홈)', ''))
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    WHERE account_row.user_id BETWEEN ? AND ?
                      AND REPLACE(transaction_row.desc3, '홈)', '') IN (
                          '우아한형제들', '쿠팡이츠', '위대한상상', '카카오모빌리티', '구글코리아',
                          '로지올', '쿠팡풀필먼트서비스', '미소', '알바몬', '도그메이트', '엠브레인패널파워'
                      )
                    """));
            assertEquals(0, count(jdbc, """
                    SELECT COUNT(*) FROM (
                        SELECT transaction_row.account_id, transaction_row.fingerprint
                        FROM ACCOUNT_TRANSACTION transaction_row
                        JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                        WHERE account_row.user_id BETWEEN ? AND ?
                        GROUP BY transaction_row.account_id, transaction_row.fingerprint
                        HAVING COUNT(*) > 1
                    ) duplicate_fingerprint
                    """));
            assertEquals(0, count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    WHERE account_row.user_id BETWEEN ? AND ?
                      AND transaction_row.desc2 IN ('입금', '출금')
                    """));
            assertEquals(180, count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    WHERE account_row.user_id BETWEEN ? AND ?
                      AND account_row.organization_code = '0003'
                      AND transaction_row.desc3 IN (
                          '우아한형제들', '쿠팡이츠', '위대한상상', '카카오모빌리티', '구글코리아',
                          '로지올', '쿠팡풀필먼트서비스', '미소', '알바몬', '도그메이트', '엠브레인패널파워'
                      )
                      AND transaction_row.desc1 IS NOT NULL
                    """));
            assertEquals(225, count(jdbc, """
                    SELECT COUNT(*)
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    WHERE account_row.user_id BETWEEN ? AND ?
                      AND REPLACE(transaction_row.desc3, '홈)', '') IN (
                          '삼성생명 실손보험', '현대해상 건강보험', 'DB손해보험 운전자보험',
                          'KB손해보험 암보험', '교보생명 종신보험', '한화생명 연금보험'
                      )
                    """));
            assertEquals(6, count(jdbc, """
                    SELECT COUNT(DISTINCT REPLACE(transaction_row.desc3, '홈)', ''))
                    FROM ACCOUNT_TRANSACTION transaction_row
                    JOIN ACCOUNT account_row ON account_row.account_id = transaction_row.account_id
                    WHERE account_row.user_id BETWEEN ? AND ?
                      AND REPLACE(transaction_row.desc3, '홈)', '') IN (
                          '삼성생명 실손보험', '현대해상 건강보험', 'DB손해보험 운전자보험',
                          'KB손해보험 암보험', '교보생명 종신보험', '한화생명 연금보험'
                      )
                    """));

            System.out.println("VIRTUAL_FINANCIAL_USERS=" + first.users());
            System.out.println("VIRTUAL_FINANCIAL_ACCOUNTS=" + first.accounts());
            System.out.println("VIRTUAL_FINANCIAL_INCOME_COUNTERPARTIES=" + first.incomeCounterparties());
            System.out.println("VIRTUAL_FINANCIAL_TRANSACTIONS=" + first.transactions());
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class, FIRST_USER_ID, LAST_USER_ID);
        return value == null ? 0 : value;
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {
            AccountMapper.class,
            AccountTransactionMapper.class,
            CodefConnectionMapper.class
    })
    static class TestConfig {

        @Bean(destroyMethod = "close")
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(
                    "jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            );
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

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        VirtualConnectionService virtualConnectionService(CodefConnectionMapper mapper) {
            return new VirtualConnectionService(mapper);
        }

        @Bean
        VirtualFinancialTransactionGenerator virtualFinancialTransactionGenerator() {
            return new VirtualFinancialTransactionGenerator();
        }

        @Bean
        VirtualFinancialDataService virtualFinancialDataService(
                VirtualConnectionService connectionService,
                AccountMapper accountMapper,
                AccountTransactionMapper transactionMapper,
                VirtualFinancialTransactionGenerator generator
        ) {
            return new VirtualFinancialDataService(
                    connectionService, accountMapper, transactionMapper, generator
            );
        }
    }
}
