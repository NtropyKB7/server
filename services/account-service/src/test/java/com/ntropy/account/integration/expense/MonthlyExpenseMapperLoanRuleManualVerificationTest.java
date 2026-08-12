package com.ntropy.account.integration.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ntropy.account.mapper.MonthlyExpenseMapper;
import com.ntropy.common.dto.account.CategoryExpenseAmount;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #143: LOAN 거래 원금 제외·이자 포함 규칙이 실제 MySQL에서 의도한 대로 동작하는지 검증하는
 * 수동 테스트입니다. {@code MonthlyExpenseMapperContractTest}는 XML 텍스트만 확인하므로
 * GREATEST/CASE/LEFT JOIN 같은 실제 SQL 문법 오류나 계산값 자체는 여기서만 잡을 수 있습니다.
 * RUN_MONTHLY_EXPENSE_LOAN_RULE_TEST=true일 때만 실행합니다.
 */
class MonthlyExpenseMapperLoanRuleManualVerificationTest {

    private static final Long USER_ID = 9_999_999_143L;
    private static final YearMonth TARGET_MONTH = YearMonth.of(2031, 1);

    @Test
    void loanInterestOnlyRuleAppliesConsistentlyAcrossTotalCategoryAndFixedExpense() throws Exception {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_MONTHLY_EXPENSE_LOAN_RULE_TEST")),
                "실제 MySQL이 필요한 이슈 #143 LOAN 소비 규칙 수동 검증용 테스트"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            seedFixtures(dataSource);

            MonthlyExpenseMapper mapper = context.getBean(MonthlyExpenseMapper.class);
            LocalDate startDate = TARGET_MONTH.atDay(1);
            LocalDate endDate = TARGET_MONTH.plusMonths(1).atDay(1);

            Long totalExpense = mapper.findTotalExpense(USER_ID, startDate, endDate);
            Long fixedExpense = mapper.findFixedExpense(USER_ID, startDate, endDate);
            List<CategoryExpenseAmount> categoryRows = mapper.findCategoryExpenses(USER_ID, startDate, endDate);
            Map<String, Long> categories = categoryRows.stream()
                    .collect(Collectors.toMap(CategoryExpenseAmount::getCategory, CategoryExpenseAmount::getExpenseAmount));

            // T1(FOOD/VARIABLE 50,000) + T2(HOUSING/FIXED 200,000) + T3(비소비, 제외)
            // + T4(LOAN 원금+이자 중 이자 50,000) + T5(LOAN 원금만, 이자 null → 0)
            // + T6(LOAN 이자만 30,000) + T7(LOAN out_amount 불일치, 이자 20,000만 반영)
            // + T8(LOAN 이자 음수 → 제외) + T10(INSTALLMENT, 제외)
            // + T11(대출실행, 양수 이자가 있어도 제외)
            assertEquals(350_000L, totalExpense, "총소비: LOAN 원금은 제외하고 이자만 반영해야 합니다");
            assertEquals(300_000L, fixedExpense,
                    "고정지출: HOUSING(FIXED)과 LOAN 이자(FINANCE)는 포함, FOOD(VARIABLE)는 제외해야 합니다");
            assertEquals(Map.of("FOOD", 50_000L, "HOUSING", 200_000L, "FINANCE", 100_000L), categories,
                    "카테고리별 소비: LOAN 이자는 모두 FINANCE로 합산되어야 합니다");
            assertEquals(totalExpense, categories.values().stream().mapToLong(Long::longValue).sum(),
                    "카테고리별 합계는 총소비와 일치해야 합니다");
        }
    }

    private void seedFixtures(DataSource dataSource) throws Exception {
        String day5 = TARGET_MONTH.atDay(5).toString();
        String previousMonthDay5 = TARGET_MONTH.minusMonths(1).atDay(5).toString();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(
                    "DELETE FROM TXN_ANALYSIS WHERE account_transaction_id IN ("
                            + "SELECT account_transaction_id FROM ACCOUNT_TRANSACTION WHERE account_id IN ("
                            + "SELECT account_id FROM ACCOUNT WHERE user_id = " + USER_ID + "))"
            );
            statement.execute(
                    "DELETE FROM ACCOUNT_TRANSACTION WHERE account_id IN ("
                            + "SELECT account_id FROM ACCOUNT WHERE user_id = " + USER_ID + ")"
            );
            statement.execute("DELETE FROM ACCOUNT WHERE user_id = " + USER_ID);
            statement.execute("DELETE FROM CODEF_CONNECTION WHERE user_id = " + USER_ID);

            statement.execute(
                    "INSERT INTO CODEF_CONNECTION (user_id, provider, connected_id) VALUES ("
                            + USER_ID + ", 'NTROPY', 'NTROPY-e2e-143')"
            );
            long connectionId = lastInsertId(statement);

            long ordinaryAccount = insertAccount(statement, connectionId, "DEPOSIT_TRUST", "11", "1101");
            long loanAccount = insertAccount(statement, connectionId, "LOAN", "40", "1102");
            long installmentAccount = insertAccount(statement, connectionId, "DEPOSIT_TRUST", "12", "1103");

            // T1: ORDINARY 변동비 소비
            insertOrdinaryTransaction(statement, ordinaryAccount, day5, 50_000, "e2e-143-t1");
            insertClassification(statement, true, "FOOD", "VARIABLE");

            // T2: ORDINARY 고정비 소비
            insertOrdinaryTransaction(statement, ordinaryAccount, day5, 200_000, "e2e-143-t2");
            insertClassification(statement, true, "HOUSING", "FIXED");

            // T3: ORDINARY 비소비 거래 - 총소비·카테고리·고정지출 어디에도 포함되면 안 된다.
            insertOrdinaryTransaction(statement, ordinaryAccount, day5, 80_000, "e2e-143-t3");
            insertClassification(statement, false, null, null);

            // T4: LOAN 원금+이자, out_amount와 원금+이자 합이 일치 - 이자 50,000만 반영.
            insertLoanTransaction(statement, loanAccount, day5, 300_000, 250_000, 50_000, "e2e-143-t4");

            // T5: LOAN 원금만(이자 null) - 총상환액 전체가 소비로 반영되면 안 된다(0 기여).
            insertLoanTransaction(statement, loanAccount, day5, 250_000, 250_000, null, "e2e-143-t5");

            // T6: LOAN 이자만 - 이자 전액 반영.
            insertLoanTransaction(statement, loanAccount, day5, 30_000, null, 30_000, "e2e-143-t6");

            // T7: LOAN out_amount(999,000)가 원금+이자 합(120,000)과 다름 - out_amount 무시하고 이자 20,000만 반영.
            insertLoanTransaction(statement, loanAccount, day5, 999_000, 100_000, 20_000, "e2e-143-t7");

            // T8: LOAN 이자가 음수(비정상 값) - 집계 대상에서 제외돼 총소비를 깎지 않아야 한다.
            insertLoanTransaction(statement, loanAccount, day5, 95_000, 100_000, -5_000, "e2e-143-t8");

            // T9: 대상월 밖 LOAN 거래 - 날짜 필터로 제외되어야 한다.
            insertLoanTransaction(statement, loanAccount, previousMonthDay5, 999_999, null, 999_999, "e2e-143-t9");

            // T10: INSTALLMENT 거래, TXN_ANALYSIS 없음 - LEFT JOIN으로 바뀌어도 여전히 제외되어야 한다.
            statement.execute(
                    "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, tran_date, "
                            + "out_amount, in_amount, after_balance) VALUES ("
                            + installmentAccount + ", SHA2('e2e-143-t10', 256), 'INSTALLMENT', '" + day5 + "', "
                        + "150000, 0, 150000)"
            );

            // T11: 대출실행 거래 - 양수 이자가 있어도 상환 거래가 아니므로 제외돼야 한다.
            insertLoanTransaction(
                    statement, loanAccount, day5, 1_040_000, 1_000_000, 40_000,
                    "대출실행", "e2e-143-t11"
            );
        }
    }

    private long insertAccount(
            Statement statement, long connectionId, String accountGroup, String depositTypeCode, String accountNoSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT (codef_connection_id, user_id, organization_code, account_group, "
                        + "deposit_type_code, account_no_masked, account_no_hash, balance, currency_code, "
                        + "overdraft_yn, status) VALUES ("
                        + connectionId + ", " + USER_ID + ", '0088', '" + accountGroup + "', '" + depositTypeCode
                        + "', '****" + accountNoSeed + "', SHA2('e2e-143-account-" + accountNoSeed + "', 256), "
                        + "1000000, 'KRW', 0, 'ACTIVE')"
        );
        return lastInsertId(statement);
    }

    private void insertOrdinaryTransaction(
            Statement statement, long accountId, String date, int outAmount, String fingerprintSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, tran_date, "
                        + "out_amount, in_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'ORDINARY', '" + date + "', "
                        + outAmount + ", 0, 1000000)"
        );
    }

    private void insertLoanTransaction(
            Statement statement, long accountId, String date, int outAmount,
            Integer principalAmount, Integer interestAmount, String fingerprintSeed
    ) throws Exception {
        insertLoanTransaction(
                statement, accountId, date, outAmount, principalAmount, interestAmount, null, fingerprintSeed
        );
    }

    private void insertLoanTransaction(
            Statement statement, long accountId, String date, int outAmount,
            Integer principalAmount, Integer interestAmount, String transactionTypeName, String fingerprintSeed
    ) throws Exception {
        String principalValue = principalAmount == null ? "NULL" : String.valueOf(principalAmount);
        String interestValue = interestAmount == null ? "NULL" : String.valueOf(interestAmount);
        String transactionTypeValue = transactionTypeName == null ? "NULL" : "'" + transactionTypeName + "'";
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, "
                        + "loan_transaction_type_name, tran_date, out_amount, in_amount, "
                        + "loan_principal_amount, loan_interest_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'LOAN', "
                        + transactionTypeValue + ", '" + date + "', " + outAmount + ", 0, "
                        + principalValue + ", " + interestValue + ", 1000000)"
        );
    }

    private void insertClassification(
            Statement statement, boolean isConsumption, String category, String expenseType
    ) throws Exception {
        long transactionId = lastInsertId(statement);
        String categoryValue = category == null ? "NULL" : "'" + category + "'";
        String expenseTypeValue = expenseType == null ? "NULL" : "'" + expenseType + "'";
        statement.execute(
                "INSERT INTO TXN_ANALYSIS (account_transaction_id, is_consumption, category, expense_type) "
                        + "VALUES (" + transactionId + ", " + isConsumption + ", " + categoryValue + ", "
                        + expenseTypeValue + ")"
        );
    }

    private long lastInsertId(Statement statement) throws Exception {
        try (var rs = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = MonthlyExpenseMapper.class)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getenv().getOrDefault(
                    "ACCOUNT_TEST_DB_URL",
                    "jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            ));
            config.setUsername(System.getenv().getOrDefault("ACCOUNT_TEST_DB_USERNAME", "root"));
            config.setPassword(System.getenv().getOrDefault("ACCOUNT_TEST_DB_PASSWORD", "root"));
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
    }
}
