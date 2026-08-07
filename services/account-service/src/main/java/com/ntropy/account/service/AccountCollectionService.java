package com.ntropy.account.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ntropy.account.client.codef.CodefBankTransactionClient;
import com.ntropy.account.client.codef.CodefInstallmentSavingsClient;
import com.ntropy.account.client.codef.CodefLoanTransactionClient;
import com.ntropy.account.client.codef.parser.AccountResponseParser;
import com.ntropy.account.client.codef.parser.AccountResponseParser.ParsedAccount;
import com.ntropy.account.client.codef.parser.AccountTransactionResponseParser;
import com.ntropy.account.client.codef.parser.InstallmentSavingsResponseParser;
import com.ntropy.account.client.codef.parser.InstallmentSavingsResponseParser.ParsedInstallmentSavings;
import com.ntropy.account.client.codef.parser.LoanTransactionResponseParser;
import com.ntropy.account.client.codef.parser.LoanTransactionResponseParser.ParsedLoan;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 은행 계정 등록부터 보유계좌·수시입출 거래내역 저장까지 조합한다.
 * 계좌 원문 번호는 저장하지 않으므로, 보유계좌 응답을 받은 바로 그 요청 흐름 안에서
 * 거래내역 조회까지 이어서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class AccountCollectionService {

    private static final String ORDINARY_DEPOSIT = "10";
    private static final String ORDINARY_WITHDRAWAL = "11";
    private static final String REGULAR_SAVINGS = "12";
    private static final String LOAN = "40";
    private static final int DEFAULT_SAVINGS_PAYMENT_DAY = 14;

    private final PersonalBankAccountService personalBankAccountService;
    private final CodefConnectionMapper codefConnectionMapper;
    private final CodefBankTransactionClient codefBankTransactionClient;
    private final CodefInstallmentSavingsClient codefInstallmentSavingsClient;
    private final CodefLoanTransactionClient codefLoanTransactionClient;
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper accountTransactionMapper;

    public List<Account> registerAndCollect(Long userId, PersonalBank bank, String loginId, String rawPassword,
                                            String birthDate, LocalDate transactionStartDate,
                                            LocalDate transactionEndDate) {
        personalBankAccountService.registerPersonalAccount(userId, bank, loginId, rawPassword, birthDate);
        return collect(userId, bank, birthDate, transactionStartDate, transactionEndDate);
    }

    public List<Account> collect(Long userId, PersonalBank bank, String birthDate,
                                 LocalDate transactionStartDate, LocalDate transactionEndDate) {
        CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (connection == null || connection.getConnectedId() == null
                || connection.getConnectedId().isBlank()) {
            throw new IllegalStateException("등록된 CODEF 연결이 없습니다");
        }
        String normalizedBirthDate = bank.normalizeBirthDate(birthDate);

        JsonNode accountListResponse = personalBankAccountService.getPersonalAccountList(userId, bank);
        List<ParsedAccount> parsedAccounts = AccountResponseParser.parse(
                accountListResponse.path("data"), connection.getId(), userId, bank.getOrganizationCode()
        );

        List<SavedAccountContext> savedContexts = new ArrayList<>();
        for (ParsedAccount parsed : parsedAccounts) {
            accountMapper.upsert(parsed.account());
            Account saved = accountMapper.findByConnectionIdAndAccountNoHash(
                    connection.getId(), parsed.account().getAccountNoHash()
            );
            savedContexts.add(new SavedAccountContext(saved, parsed.rawAccountNo()));
        }

        List<IllegalStateException> collectionFailures = new ArrayList<>();
        for (SavedAccountContext context : savedContexts) {
            Account saved = context.account();
            try {
                collectEligibleTransactions(
                        bank, connection.getConnectedId(), context.rawAccountNo(), saved,
                        normalizedBirthDate, transactionStartDate, transactionEndDate
                );
            } catch (RuntimeException e) {
                collectionFailures.add(new IllegalStateException(
                        "CODEF 거래 수집 실패: accountId=" + saved.getId()
                                + ", group=" + saved.getAccountGroup()
                                + ", depositTypeCode=" + saved.getDepositTypeCode(),
                        e
                ));
            }
        }

        if (!collectionFailures.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(
                    "CODEF 거래내역 수집 일부 실패: " + collectionFailures.size() + "개 계좌"
            );
            collectionFailures.forEach(aggregate::addSuppressed);
            throw aggregate;
        }

        return savedContexts.stream().map(SavedAccountContext::account).toList();
    }

    private void collectEligibleTransactions(PersonalBank bank, String connectedId, String rawAccountNo,
                                             Account saved, String birthDate,
                                             LocalDate startDate, LocalDate endDate) {
        if (isOrdinaryTransactionEligible(saved, bank)) {
            collectTransactions(
                    bank, connectedId, rawAccountNo, saved.getId(), birthDate, startDate, endDate
            );
        } else if (isInstallmentSavingsEligible(saved)) {
            collectInstallmentSavings(
                    bank, connectedId, rawAccountNo, saved, birthDate, startDate, endDate
            );
        } else if (isLoanEligible(saved)) {
            collectLoanTransactions(
                    bank, connectedId, rawAccountNo, saved, birthDate, startDate, endDate
            );
        }
    }

    private record SavedAccountContext(Account account, String rawAccountNo) {
    }

    private void collectLoanTransactions(PersonalBank bank, String connectedId, String rawAccountNo,
                                         Account account, String birthDate,
                                         LocalDate startDate, LocalDate endDate) {
        JsonNode response = codefLoanTransactionClient.getPersonalTransactionList(
                bank.getOrganizationCode(), connectedId, rawAccountNo, null,
                startDate, endDate, birthDate
        );
        List<ParsedLoan> parsedResults = LoanTransactionResponseParser.parse(response.path("data"), account.getId());
        for (ParsedLoan parsed : parsedResults) {
            accountMapper.updateAccountDetails(parsed.detail());
            List<AccountTransaction> transactions = parsed.transactions();
            if (!transactions.isEmpty()) {
                accountTransactionMapper.insertAll(transactions);
            }
        }
    }

    private void collectInstallmentSavings(PersonalBank bank, String connectedId, String rawAccountNo,
                                           Account account, String birthDate,
                                           LocalDate startDate, LocalDate endDate) {
        JsonNode response = codefInstallmentSavingsClient.getPersonalTransactionList(
                bank.getOrganizationCode(), connectedId, rawAccountNo, startDate, endDate, birthDate
        );
        List<ParsedInstallmentSavings> parsedResults = InstallmentSavingsResponseParser.parse(
                response.path("data"), account.getId()
        );
        List<AccountTransaction> collectedTransactions = new ArrayList<>();
        for (ParsedInstallmentSavings parsed : parsedResults) {
            accountMapper.updateAccountDetails(parsed.detail());
            List<AccountTransaction> transactions = parsed.transactions();
            if (!transactions.isEmpty()) {
                accountTransactionMapper.insertAll(transactions);
                collectedTransactions.addAll(transactions);
            }
        }
        int preferredDay = collectedTransactions.stream()
                .map(AccountTransaction::getTranDate)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(LocalDate::getDayOfMonth)
                .orElseGet(() -> paymentDay(account));
        updateNextPaymentDate(account.getId(), nextOccurrence(endDate, preferredDay));
    }

    private void updateNextPaymentDate(Long accountId, LocalDate nextPaymentDate) {
        Account schedule = new Account();
        schedule.setId(accountId);
        schedule.setNextPaymentDate(nextPaymentDate);
        accountMapper.updateAccountDetails(schedule);
    }

    private static int paymentDay(Account account) {
        return account.getAccountStartDate() != null
                ? account.getAccountStartDate().getDayOfMonth()
                : DEFAULT_SAVINGS_PAYMENT_DAY;
    }

    private static LocalDate nextOccurrence(LocalDate referenceDate, int preferredDay) {
        YearMonth month = YearMonth.from(referenceDate);
        LocalDate candidate = month.atDay(Math.min(preferredDay, month.lengthOfMonth()));
        if (!candidate.isAfter(referenceDate)) {
            YearMonth nextMonth = month.plusMonths(1);
            candidate = nextMonth.atDay(Math.min(preferredDay, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }

    private void collectTransactions(PersonalBank bank, String connectedId, String rawAccountNo, Long accountId,
                                     String birthDate, LocalDate startDate, LocalDate endDate) {
        JsonNode transactionListResponse = codefBankTransactionClient.getPersonalTransactionList(
                bank.getOrganizationCode(), connectedId, rawAccountNo, startDate, endDate, birthDate
        );
        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(
                transactionListResponse.path("data"), accountId
        );
        if (!transactions.isEmpty()) {
            accountTransactionMapper.insertAll(transactions);
        }
    }

    /**
     * 수시입출 거래내역 API 대상은 예금/신탁 그룹 중 미분류(10)·수시입출(11) 계좌뿐이다.
     * SC은행은 거래내역 조회에 accountPassword가 추가로 필요한데 이번 이슈에서는 다루지 않으므로 제외한다.
     */
    private static boolean isOrdinaryTransactionEligible(Account account, PersonalBank bank) {
        return account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST
                && (ORDINARY_DEPOSIT.equals(account.getDepositTypeCode())
                        || ORDINARY_WITHDRAWAL.equals(account.getDepositTypeCode()))
                && bank != PersonalBank.SC_BANK;
    }

    private static boolean isInstallmentSavingsEligible(Account account) {
        return account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST
                && REGULAR_SAVINGS.equals(account.getDepositTypeCode());
    }

    private static boolean isLoanEligible(Account account) {
        return account.getAccountGroup() == AccountGroup.LOAN || LOAN.equals(account.getDepositTypeCode());
    }
}
