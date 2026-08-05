package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.ntropy.account.domain.PlatformMatchStatus;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator.GeneratedTransactions;

class VirtualFinancialTransactionGeneratorTest {

    private static final Set<String> INSURANCE_PRODUCTS = Set.of(
            "삼성생명 실손보험", "현대해상 건강보험", "DB손해보험 운전자보험",
            "KB손해보험 암보험", "교보생명 종신보험", "한화생명 연금보험"
    );
    private static final Set<String> PLATFORM_DEPOSIT_NAMES = Set.of(
            "우아한형제들", "쿠팡이츠", "위대한상상", "카카오모빌리티", "구글코리아",
            "로지올", "쿠팡풀필먼트서비스", "미소", "알바몬", "도그메이트", "엠브레인패널파워"
    );

    private final VirtualFinancialTransactionGenerator generator = new VirtualFinancialTransactionGenerator();

    @Test
    void generatesOneHundredTransactionsPerMonthWithAllFinancialPatterns() {
        Account ordinary = account(10L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(11L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions generated = generator.generate(
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
        assertTrue(generated.transactions().stream().anyMatch(transaction -> transaction.getInAmount().signum() > 0));
        assertTrue(generated.transactions().stream().anyMatch(transaction -> transaction.getOutAmount().signum() > 0));
        List<AccountTransaction> platformTransactions = generated.transactions().stream()
                .filter(transaction -> PLATFORM_DEPOSIT_NAMES.contains(transaction.getDesc3()))
                .toList();
        assertEquals(30, platformTransactions.size());
        assertEquals(Set.of("우아한형제들", "카카오모빌리티"), platformTransactions.stream()
                .map(AccountTransaction::getDesc3).collect(Collectors.toSet()));
        assertTrue(platformTransactions.stream().allMatch(transaction -> transaction.getPlatformId() == null));
        assertTrue(platformTransactions.stream().allMatch(
                transaction -> transaction.getPlatformMatchStatus() == PlatformMatchStatus.PENDING));
    }

    @Test
    void assignsThreePlatformsToEverySecondUserAndGeneratesLoanRepayments() {
        Account ordinary = account(20L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(21L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(26, PersonalBank.NH_BANK, ordinary, loan);

        assertEquals(3, generated.userPlatformCount());
        assertEquals(3, generated.transactions().stream()
                .map(AccountTransaction::getDesc3).filter(PLATFORM_DEPOSIT_NAMES::contains).distinct().count());
        assertEquals(3, countCategory(generated.transactions(), AccountTransactionCategory.LOAN));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .allMatch(transaction -> transaction.getOutAmount().signum() > 0));
    }

    @Test
    void generatesStableUniqueFingerprintsAndConsistentBalances() {
        Account ordinary = account(30L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(31L, AccountGroup.DEPOSIT_TRUST);

        GeneratedTransactions first = generator.generate(3, PersonalBank.KB_KOOKMIN_BANK, ordinary, installment);
        GeneratedTransactions second = generator.generate(3, PersonalBank.KB_KOOKMIN_BANK, ordinary, installment);

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

        GeneratedTransactions jeonbuk = generator.generate(
                5, PersonalBank.JEONBUK_BANK, ordinary, installment
        );
        assertTrue(jeonbuk.transactions().stream().allMatch(transaction -> transaction.getDesc2() == null));

        GeneratedTransactions ibk = generator.generate(
                5, PersonalBank.IBK_INDUSTRIAL_BANK, ordinary, installment
        );
        assertTrue(ibk.transactions().stream().allMatch(transaction -> transaction.getDesc4() == null));
        assertTrue(ibk.transactions().stream()
                .filter(transaction -> PLATFORM_DEPOSIT_NAMES.contains(transaction.getDesc3()))
                .allMatch(transaction -> transaction.getDesc1() != null));
        assertNull(ibk.transactions().get(0).getDesc4());
    }

    @Test
    void appliesConsumerProfileToMerchantMixAndSpendingLevel() {
        GeneratedTransactions rational = generator.generate(
                1, PersonalBank.SHINHAN_BANK,
                account(50L, AccountGroup.DEPOSIT_TRUST), account(51L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions trend = generator.generate(
                2, PersonalBank.SHINHAN_BANK,
                account(52L, AccountGroup.DEPOSIT_TRUST), account(53L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions value = generator.generate(
                3, PersonalBank.SHINHAN_BANK,
                account(54L, AccountGroup.DEPOSIT_TRUST), account(55L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions impulse = generator.generate(
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
    void assignsOneOrTwoInsuranceProductsAndPreservesBankDescriptions() {
        GeneratedTransactions oddUser = generator.generate(
                1, PersonalBank.IBK_INDUSTRIAL_BANK,
                account(60L, AccountGroup.DEPOSIT_TRUST), account(61L, AccountGroup.DEPOSIT_TRUST)
        );
        GeneratedTransactions evenUser = generator.generate(
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
