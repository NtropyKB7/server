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

import com.ntropy.account.mapper.FinancialCommitmentMapper;
import com.ntropy.account.mapper.MonthlyExpenseMapper;
import com.ntropy.account.mapper.projection.LoanCommitmentCandidateRow;
import com.ntropy.common.domain.LoanDisbursementKeywords;
import com.ntropy.common.dto.account.CategoryExpenseAmount;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 이슈 #143/#169: LOAN 거래 원금 제외·이자 포함 규칙, INSTALLMENT 적립액(in_amount) 집계 규칙,
 * FinancialCommitmentMapper의 최근 정상 상환 선택 규칙이 실제 MySQL에서 의도한 대로
 * 동작하는지 검증하는 수동 테스트입니다.
 * {@code MonthlyExpenseMapperContractTest}는 XML 텍스트만 확인하므로 GREATEST/CASE/LEFT JOIN/
 * REGEXP_REPLACE 같은 실제 SQL 문법 오류나 계산값 자체는 여기서만 잡을 수 있습니다.
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
            FinancialCommitmentMapper financialCommitmentMapper =
                    context.getBean(FinancialCommitmentMapper.class);
            LocalDate startDate = TARGET_MONTH.atDay(1);
            LocalDate endDate = TARGET_MONTH.plusMonths(1).atDay(1);
            List<String> loanDisbursementKeywords = LoanDisbursementKeywords.KEYWORDS;

            Long totalExpense = mapper.findTotalExpense(USER_ID, startDate, endDate, loanDisbursementKeywords);
            Long fixedExpense = mapper.findFixedExpense(USER_ID, startDate, endDate, loanDisbursementKeywords);
            List<CategoryExpenseAmount> categoryRows =
                    mapper.findCategoryExpenses(USER_ID, startDate, endDate, loanDisbursementKeywords);
            Map<String, Long> categories = categoryRows.stream()
                    .collect(Collectors.toMap(CategoryExpenseAmount::getCategory, CategoryExpenseAmount::getExpenseAmount));

            // T1(FOOD/VARIABLE 50,000) + T2(HOUSING/FIXED 200,000) + T3(비소비, 제외)
            // + T4(LOAN 원금+이자 중 이자 50,000) + T5(LOAN 원금만, 이자 null → 0)
            // + T6(LOAN 이자만 30,000) + T7(LOAN out_amount 불일치, 이자 20,000만 반영)
            // + T8(LOAN 이자 음수 → 제외) + T9(대상월 밖 LOAN, 제외)
            // + T10(INSTALLMENT, TXN_ANALYSIS 없음, in_amount 150,000 반영)
            // + T11(대출실행, 양수 이자가 있어도 제외)
            // + T12(INSTALLMENT, TXN_ANALYSIS 있음, in_amount 80,000 반영, 중복 합산 없음)
            // + T13(INSTALLMENT, in_amount=0 → 제외) + T14(대상월 밖 INSTALLMENT, 제외)
            assertEquals(580_000L, totalExpense,
                    "총소비: LOAN 원금은 제외하고 이자만, INSTALLMENT는 out_amount가 아닌 in_amount만 반영해야 합니다");
            assertEquals(530_000L, fixedExpense,
                    "고정지출: HOUSING(FIXED)·LOAN 이자(FINANCE)·INSTALLMENT(FINANCE)는 포함, "
                            + "FOOD(VARIABLE)는 제외해야 합니다");
            assertEquals(Map.of("FOOD", 50_000L, "HOUSING", 200_000L, "FINANCE", 330_000L), categories,
                    "카테고리별 소비: LOAN 이자·INSTALLMENT는 모두 FINANCE로 합산되어야 합니다");
            assertEquals(totalExpense, categories.values().stream().mapToLong(Long::longValue).sum(),
                    "카테고리별 합계는 총소비와 일치해야 합니다");

            List<LoanCommitmentCandidateRow> loanCommitments =
                    financialCommitmentMapper.findLoanCommitmentCandidates(
                            USER_ID,
                            loanDisbursementKeywords
                    );
            assertEquals(1, loanCommitments.size(), "LOAN 계좌별 후보는 한 건이어야 합니다");
            LoanCommitmentCandidateRow latestRepayment = loanCommitments.get(0);
            assertEquals(123_000L, latestRepayment.getExpectedAmount().longValueExact(),
                    "공백 포함 지급 거래들을 건너뛰고 직전 정상 상환을 선택해야 합니다");
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

            // T10: INSTALLMENT 거래, TXN_ANALYSIS 없음 - out_amount가 아닌 in_amount(150,000)가
            // TXN_ANALYSIS 유무와 무관하게 반영되어야 한다(이슈 #169, 기존에는 0원으로 집계되던 버그).
            insertInstallmentTransaction(statement, installmentAccount, day5, 150_000, "e2e-169-t10");

            // T11: 대출실행 거래 - 양수 이자가 있어도 상환 거래가 아니므로 제외돼야 한다.
            insertLoanTransaction(
                    statement, loanAccount, day5, 1_040_000, 1_000_000, 40_000,
                    "대출실행", "e2e-143-t11"
            );

            // T12: INSTALLMENT 거래, TXN_ANALYSIS 있음(TRUE/FINANCE/FIXED) - 분석 행이 있어도
            // in_amount(80,000)가 정확히 한 번만 합산되어야 한다(중복 집계 없음).
            insertInstallmentTransaction(statement, installmentAccount, day5, 80_000, "e2e-169-t12");
            insertClassification(statement, true, "FINANCE", "FIXED");

            // T13: INSTALLMENT 거래, in_amount=0 - 집계에 기여하지 않아야 한다.
            insertInstallmentTransaction(statement, installmentAccount, day5, 0, "e2e-169-t13");

            // T14: 대상월 밖 INSTALLMENT 거래 - 날짜 필터로 제외되어야 한다.
            insertInstallmentTransaction(statement, installmentAccount, previousMonthDay5, 999_999, "e2e-169-t14");

            // T15: FinancialCommitmentMapper가 선택해야 할 최근 정상 상환. 이자가 NULL이어서
            // 월간 소비에는 기여하지 않지만 대출 납입 예정 후보에는 포함된다.
            insertLoanTransaction(
                    statement, loanAccount, day5, 123_000, 123_000, null,
                    "정상상환", "e2e-169-t15"
            );

            // T16~T18: T15보다 나중에 저장된 공백 포함 지급 거래. 월간 소비에서 제외되어야 하고,
            // FinancialCommitmentMapper도 이 거래들을 건너뛰어 T15를 최근 정상 상환으로 선택해야 한다.
            insertLoanTransaction(
                    statement, loanAccount, day5, 2_100_000, 2_000_000, 100_000,
                    "신 규", "e2e-169-t16"
            );
            insertLoanTransaction(
                    statement, loanAccount, day5, 2_100_000, 2_000_000, 100_000,
                    "실 행", "e2e-169-t17"
            );
            insertLoanTransaction(
                    statement, loanAccount, day5, 2_100_000, 2_000_000, 100_000,
                    "증 액", "e2e-169-t18"
            );
        }
    }

    private void insertInstallmentTransaction(
            Statement statement, long accountId, String date, int inAmount, String fingerprintSeed
    ) throws Exception {
        statement.execute(
                "INSERT INTO ACCOUNT_TRANSACTION (account_id, fingerprint, transaction_category, tran_date, "
                        + "out_amount, in_amount, after_balance) VALUES ("
                        + accountId + ", SHA2('" + fingerprintSeed + "', 256), 'INSTALLMENT', '" + date + "', "
                        + "0, " + inAmount + ", 1000000)"
        );
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
