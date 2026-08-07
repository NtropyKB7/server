package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.AccountBalanceConsistencyValidator;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator.GeneratedTransactions;

class VirtualFinancialTransactionGeneratorTest {

    private static final Set<String> INSURANCE_PRODUCTS = Set.of(
            "삼성생명 실손보험", "현대해상 건강보험", "DB손해보험 운전자보험",
            "KB손해보험 암보험", "교보생명 종신보험", "한화생명 연금보험"
    );
    private static final Set<String> INCOME_COUNTERPARTY_NAMES = Set.of(
            "우아한형제들", "쿠팡이츠", "위대한상상", "카카오모빌리티", "구글코리아",
            "로지올", "쿠팡풀필먼트서비스", "미소", "알바몬", "도그메이트", "엠브레인패널파워"
    );

    // 월말을 기준일로 고정해 "현재 월"이 항상 완결된 3개월 창(2026-04~06)이 되도록 하고, 기존 고정 기간 검증값을 그대로 유지한다.
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 6, 30);

    private final VirtualFinancialTransactionGenerator generator = new VirtualFinancialTransactionGenerator();

    @Test
    void generatesOneHundredTransactionsPerMonthWithAllFinancialPatterns() {
        Account ordinary = account(10L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(11L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                1, PersonalBank.SHINHAN_BANK, ordinary, installment
        );

        assertEquals(300, generated.transactions().size());
        Map<YearMonth, Long> monthlyCounts = generated.transactions().stream()
                .collect(Collectors.groupingBy(
                        transaction -> YearMonth.from(transaction.getTranDate()),
                        Collectors.counting()
                ));
        assertEquals(100L, monthlyCounts.get(YearMonth.of(2026, 4)));
        assertEquals(100L, monthlyCounts.get(YearMonth.of(2026, 5)));
        assertEquals(100L, monthlyCounts.get(YearMonth.of(2026, 6)));

        assertEquals(297, countCategory(generated.transactions(), AccountTransactionCategory.ORDINARY));
        assertEquals(3, countCategory(generated.transactions(), AccountTransactionCategory.INSTALLMENT));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.INSTALLMENT)
                .allMatch(transaction -> transaction.getTranDate().getDayOfMonth() == 25));
        assertTrue(generated.transactions().stream().anyMatch(transaction -> transaction.getInAmount().signum() > 0));
        assertTrue(generated.transactions().stream().anyMatch(transaction -> transaction.getOutAmount().signum() > 0));
        List<AccountTransaction> incomeTransactions = generated.transactions().stream()
                .filter(transaction -> INCOME_COUNTERPARTY_NAMES.contains(transaction.getDesc3()))
                .toList();
        assertEquals(30, incomeTransactions.size());
        assertEquals(Set.of("우아한형제들", "카카오모빌리티"), incomeTransactions.stream()
                .map(AccountTransaction::getDesc3).collect(Collectors.toSet()));
    }

    @Test
    void assignsThreeIncomeCounterpartiesToEverySecondUserAndGeneratesLoanRepayments() {
        Account ordinary = account(20L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(21L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(REFERENCE_DATE,26, PersonalBank.NH_BANK, ordinary, loan);

        assertEquals(3, generated.userIncomeCounterpartyCount());
        assertEquals(3, generated.transactions().stream()
                .map(AccountTransaction::getDesc3).filter(INCOME_COUNTERPARTY_NAMES::contains).distinct().count());
        assertEquals(3, countCategory(generated.transactions(), AccountTransactionCategory.LOAN));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .allMatch(transaction -> transaction.getOutAmount().signum() > 0));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .allMatch(transaction -> transaction.getTranDate().getDayOfMonth() == 25));
    }

    @Test
    void generatesStableUniqueFingerprintsAndConsistentBalances() {
        Account ordinary = account(30L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(31L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions first = generator.generate(REFERENCE_DATE,3, PersonalBank.KB_KOOKMIN_BANK, ordinary, installment);
        GeneratedTransactions second = generator.generate(REFERENCE_DATE,3, PersonalBank.KB_KOOKMIN_BANK, ordinary, installment);

        List<String> firstFingerprints = first.transactions().stream()
                .map(AccountTransaction::getFingerprint).toList();
        List<String> secondFingerprints = second.transactions().stream()
                .map(AccountTransaction::getFingerprint).toList();
        assertEquals(firstFingerprints, secondFingerprints);
        assertEquals(300, Set.copyOf(firstFingerprints).size());

        verifyAccountBalance(ordinary, first);
        verifyAccountBalance(installment, first);
    }

    @Test
    void followsBankSpecificOptionalDescriptionFields() {
        Account ordinary = account(40L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(41L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions jeonbuk = generator.generate(REFERENCE_DATE,
                5, PersonalBank.JEONBUK_BANK, ordinary, installment
        );
        assertTrue(jeonbuk.transactions().stream().allMatch(transaction -> transaction.getDesc2() == null));

        GeneratedTransactions ibk = generator.generate(REFERENCE_DATE,
                5, PersonalBank.IBK_INDUSTRIAL_BANK, ordinary, installment
        );
        assertTrue(ibk.transactions().stream().allMatch(transaction -> transaction.getDesc4() == null));
        assertTrue(ibk.transactions().stream()
                .filter(transaction -> INCOME_COUNTERPARTY_NAMES.contains(transaction.getDesc3()))
                .allMatch(transaction -> transaction.getDesc1() != null));
        assertNull(ibk.transactions().get(0).getDesc4());
    }

    @Test
    void appliesConsumerProfileToMerchantMixAndSpendingLevel() {
        GeneratedTransactions rational = generator.generate(REFERENCE_DATE,
                1, PersonalBank.SHINHAN_BANK,
                account(50L, AccountGroup.DEPOSIT_TRUST), account(51L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions trend = generator.generate(REFERENCE_DATE,
                2, PersonalBank.SHINHAN_BANK,
                account(52L, AccountGroup.DEPOSIT_TRUST), account(53L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions value = generator.generate(REFERENCE_DATE,
                3, PersonalBank.SHINHAN_BANK,
                account(54L, AccountGroup.DEPOSIT_TRUST), account(55L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions impulse = generator.generate(REFERENCE_DATE,
                4, PersonalBank.SHINHAN_BANK,
                account(56L, AccountGroup.DEPOSIT_TRUST), account(57L, AccountGroup.DEPOSIT_TRUST)
        );

        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.RATIONAL_FRUGAL,
                VirtualFinancialTransactionGenerator.consumerProfileFor(1));
        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.TREND_STATUS,
                VirtualFinancialTransactionGenerator.consumerProfileFor(2));
        assertTrue(hasMerchant(rational, "다이소"));
        assertTrue(hasMerchant(trend, "신세계백화점"));
        assertTrue(hasMerchant(value, "제로웨이스트샵"));
        assertTrue(hasMerchant(impulse, "구글플레이"));
        assertTrue(averageCardSpending(trend) > averageCardSpending(rational));
    }

    @Test
    void assignsLoggedInUserProfileByStableUserIdHash() {
        for (long userId = 1; userId <= 20; userId++) {
            int expectedIndex = Math.floorMod(Long.hashCode(userId), 4);
            assertEquals(
                    VirtualFinancialTransactionGenerator.ConsumerProfile.values()[expectedIndex],
                    VirtualFinancialTransactionGenerator.consumerProfileForUser(userId)
            );
        }
    }

    @Test
    void assignsOneOrTwoInsuranceProductsAndPreservesBankDescriptions() {
        GeneratedTransactions oddUser = generator.generate(REFERENCE_DATE,
                1, PersonalBank.IBK_INDUSTRIAL_BANK,
                account(60L, AccountGroup.DEPOSIT_TRUST), account(61L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions evenUser = generator.generate(REFERENCE_DATE,
                2, PersonalBank.NH_BANK,
                account(62L, AccountGroup.DEPOSIT_TRUST), account(63L, AccountGroup.DEPOSIT_TRUST)
        );

        List<AccountTransaction> oddInsurance = insuranceTransactions(oddUser);
        List<AccountTransaction> evenInsurance = insuranceTransactions(evenUser);
        assertEquals(3, oddInsurance.size());
        assertEquals(Set.of("삼성생명 실손보험"), oddInsurance.stream()
                .map(AccountTransaction::getDesc3).collect(Collectors.toSet()));
        assertTrue(oddInsurance.stream().allMatch(transaction -> "삼성생명".equals(transaction.getDesc1())));

        assertEquals(6, evenInsurance.size());
        assertEquals(2, evenInsurance.stream().map(AccountTransaction::getDesc3).distinct().count());
        assertTrue(evenInsurance.stream().allMatch(transaction -> "보험료".equals(transaction.getDesc2())));
    }

    @Test
    void doesNotGenerateFutureTransactionsInCurrentMonth() {
        LocalDate midMonthReference = LocalDate.of(2026, 6, 15);
        Account ordinary = account(70L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(71L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions generated = generator.generate(
                midMonthReference, 1, PersonalBank.SHINHAN_BANK, ordinary, installment
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(midMonthReference)));

        Map<YearMonth, Long> monthlyCounts = generated.transactions().stream()
                .collect(Collectors.groupingBy(
                        transaction -> YearMonth.from(transaction.getTranDate()),
                        Collectors.counting()
                ));
        // 지난 2개월(4~5월)은 완결된 달이라 100건씩, 현재 월(6월 15일까지)은 100건보다 적어야 한다.
        assertEquals(100L, monthlyCounts.get(YearMonth.of(2026, 4)));
        assertEquals(100L, monthlyCounts.get(YearMonth.of(2026, 5)));
        assertTrue(monthlyCounts.get(YearMonth.of(2026, 6)) < 100L);
        assertTrue(generated.transactions().size() >= 200 && generated.transactions().size() < 300);
    }

    @Test
    void shiftsThreeMonthWindowWithReferenceDate() {
        LocalDate referenceDate = LocalDate.of(2026, 11, 30);
        Account ordinary = account(72L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(73L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions generated = generator.generate(
                referenceDate, 1, PersonalBank.SHINHAN_BANK, ordinary, installment
        );

        Set<YearMonth> months = generated.transactions().stream()
                .map(transaction -> YearMonth.from(transaction.getTranDate()))
                .collect(Collectors.toSet());
        assertEquals(Set.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months);
    }

    @Test
    void splitsLoanRepaymentIntoPrincipalAndInterest() {
        Account ordinary = account(80L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(81L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                REFERENCE_DATE, 26, PersonalBank.NH_BANK, ordinary, loan
        );

        List<AccountTransaction> loanTransactions = generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .toList();
        assertEquals(3, loanTransactions.size());
        for (AccountTransaction transaction : loanTransactions) {
            assertEquals("원리금상환", transaction.getLoanTransactionTypeName());
            assertNotNull(transaction.getLoanPrincipalAmount());
            assertNotNull(transaction.getLoanInterestAmount());
            assertEquals(transaction.getOutAmount(),
                    transaction.getLoanPrincipalAmount().add(transaction.getLoanInterestAmount()));
        }
    }

    @Test
    void handlesFirstDayOfMonthAsReferenceDateWithMinimalCurrentMonthTransactions() {
        LocalDate firstOfMonth = LocalDate.of(2026, 6, 1);
        Account ordinary = account(90L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(91L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions generated = generator.generate(
                firstOfMonth, 1, PersonalBank.SHINHAN_BANK, ordinary, installment
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(firstOfMonth)));
        long currentMonthCount = generated.transactions().stream()
                .filter(transaction -> YearMonth.from(transaction.getTranDate()).equals(YearMonth.of(2026, 6)))
                .count();
        assertTrue(currentMonthCount > 0 && currentMonthCount < 10,
                "월 1일에는 지난달 100건씩 + 이번 달 소수 건만 있어야 한다: " + currentMonthCount);
    }

    @Test
    void handlesLeapYearFebruary29AsReferenceDate() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        Account ordinary = account(92L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(93L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                leapDay, 26, PersonalBank.NH_BANK, ordinary, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(leapDay)));
        assertTrue(generated.transactions().size() >= 200);
    }

    @Test
    void handlesNonLeapYearFebruary28AsReferenceDate() {
        LocalDate lastFebDay = LocalDate.of(2027, 2, 28);
        Account ordinary = account(94L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(95L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions generated = generator.generate(
                lastFebDay, 1, PersonalBank.SHINHAN_BANK, ordinary, installment
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(lastFebDay)));
        // 2월은 완결된 달이라도 100건이 아니라 28일치 패턴 그대로다.
        Map<YearMonth, Long> monthlyCounts = generated.transactions().stream()
                .collect(Collectors.groupingBy(
                        transaction -> YearMonth.from(transaction.getTranDate()),
                        Collectors.counting()
                ));
        assertEquals(100L, monthlyCounts.get(YearMonth.of(2027, 2)));
    }

    @Test
    void handlesThirtyOneDayMonthEndAsReferenceDate() {
        LocalDate endOfJuly = LocalDate.of(2026, 7, 31);
        Account ordinary = account(96L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(97L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                endOfJuly, 26, PersonalBank.NH_BANK, ordinary, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(endOfJuly)));
        assertEquals(300, generated.transactions().size());
    }

    private static long countCategory(List<AccountTransaction> transactions,
                                      AccountTransactionCategory category) {
        return transactions.stream()
                .filter(transaction -> transaction.getTransactionCategory() == category)
                .count();
    }

    private static void verifyAccountBalance(Account account, GeneratedTransactions generated) {
        account.setBalance(generated.finalBalances().get(account.getId()));
        List<AccountTransaction> accountTransactions = generated.transactions().stream()
                .filter(transaction -> account.getId().equals(transaction.getAccountId()))
                .toList();
        AccountBalanceConsistencyValidator.validate(account, accountTransactions);
    }

    private static boolean hasMerchant(GeneratedTransactions generated, String merchant) {
        return generated.transactions().stream().anyMatch(transaction -> merchant.equals(transaction.getDesc3()));
    }

    private static double averageCardSpending(GeneratedTransactions generated) {
        return generated.transactions().stream()
                .filter(transaction -> "신한체".equals(transaction.getDesc2()))
                .mapToDouble(transaction -> transaction.getOutAmount().doubleValue())
                .average()
                .orElseThrow();
    }

    private static List<AccountTransaction> insuranceTransactions(GeneratedTransactions generated) {
        return generated.transactions().stream()
                .filter(transaction -> INSURANCE_PRODUCTS.contains(transaction.getDesc3()))
                .toList();
    }

    private static Account account(Long id, AccountGroup group) {
        Account account = new Account();
        account.setId(id);
        account.setAccountGroup(group);
        return account;
    }
}
