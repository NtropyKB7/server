package com.ntropy.account.service;

import java.time.LocalDate;
import java.util.ArrayList;
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
import com.ntropy.account.exception.CredentialRequiredException;
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
        CodefConnection connection = requireCodefConnection(userId);
        String normalizedBirthDate = bank.normalizeBirthDate(birthDate);
        List<SavedAccountContext> savedContexts = fetchAndSaveAccounts(userId, bank, connection);

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

    /**
     * 일일 증분 동기화 전용(이슈 #158). {@link #collect}와 달리 계좌 하나의 실패로 전체를
     * 예외로 묶어 던지지 않고, 계좌별 결과를 모두 반환한다. 비밀번호가 필요해 조회할 수 없는
     * 계좌(SC은행)는 {@link AccountCollectionOutcome.Status#SKIPPED_CREDENTIAL_REQUIRED}로,
     * 그 외 실패는 {@link AccountCollectionOutcome.Status#FAILED}로 구분한다.
     */
    public List<AccountCollectionOutcome> collectForDailySync(Long userId, PersonalBank bank, String birthDate,
                                                               LocalDate transactionStartDate,
                                                               LocalDate transactionEndDate) {
        CodefConnection connection = requireCodefConnection(userId);
        String normalizedBirthDate = bank.normalizeBirthDate(birthDate);
        List<SavedAccountContext> savedContexts = fetchAndSaveAccounts(userId, bank, connection);

        List<AccountCollectionOutcome> outcomes = new ArrayList<>();
        for (SavedAccountContext context : savedContexts) {
            Account saved = context.account();
            try {
                int transactionCount = collectEligibleTransactions(
                        bank, connection.getConnectedId(), context.rawAccountNo(), saved,
                        normalizedBirthDate, transactionStartDate, transactionEndDate
                );
                outcomes.add(new AccountCollectionOutcome(
                        saved, AccountCollectionOutcome.Status.SUCCESS, null, transactionCount
                ));
            } catch (CredentialRequiredException e) {
                outcomes.add(new AccountCollectionOutcome(
                        saved, AccountCollectionOutcome.Status.SKIPPED_CREDENTIAL_REQUIRED,
                        "SKIPPED_CREDENTIAL_REQUIRED", 0
                ));
            } catch (RuntimeException e) {
                outcomes.add(new AccountCollectionOutcome(
                        saved, AccountCollectionOutcome.Status.FAILED, classifyFailure(e), 0
                ));
            }
        }
        return outcomes;
    }

    private CodefConnection requireCodefConnection(Long userId) {
        CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (connection == null || connection.getConnectedId() == null
                || connection.getConnectedId().isBlank()) {
            throw new IllegalStateException("등록된 CODEF 연결이 없습니다");
        }
        return connection;
    }

    /** 실행마다 보유계좌를 재조회해 원문 계좌번호를 이 요청 흐름 안에서만 확보한다(저장하지 않음). */
    private List<SavedAccountContext> fetchAndSaveAccounts(Long userId, PersonalBank bank, CodefConnection connection) {
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
        return savedContexts;
    }

    /**
     * 오류 요약(error_summary)에 남길 대략적인 분류. CODEF 오류코드 전체 매핑은 이번 범위가 아니며,
     * 원본 예외 메시지는 민감정보 노출 위험이 있어 그대로 남기지 않는다.
     */
    private static String classifyFailure(RuntimeException e) {
        String message = e.getMessage();
        if (message != null && (message.contains("타임아웃") || message.contains("시간 초과"))) {
            return "TIMEOUT";
        }
        return "COLLECTION_FAILED";
    }

    /** 계좌 단위 증분 수집 결과. 이슈 #158의 provider별 일일 동기화 결과 집계에 쓰인다. */
    public record AccountCollectionOutcome(Account account, Status status, String errorCode, int transactionCount) {
        public enum Status {
            SUCCESS,
            SKIPPED_CREDENTIAL_REQUIRED,
            FAILED
        }
    }

    private int collectEligibleTransactions(PersonalBank bank, String connectedId, String rawAccountNo,
                                            Account saved, String birthDate,
                                            LocalDate startDate, LocalDate endDate) {
        if (isOrdinaryTransactionEligible(saved)) {
            return collectTransactions(
                    bank, connectedId, rawAccountNo, saved.getId(), birthDate, startDate, endDate
            );
        } else if (isInstallmentSavingsEligible(saved)) {
            return collectInstallmentSavings(
                    bank, connectedId, rawAccountNo, saved, birthDate, startDate, endDate
            );
        } else if (isLoanEligible(saved)) {
            return collectLoanTransactions(
                    bank, connectedId, rawAccountNo, saved, birthDate, startDate, endDate
            );
        }
        return 0;
    }

    private record SavedAccountContext(Account account, String rawAccountNo) {
    }

    private int collectLoanTransactions(PersonalBank bank, String connectedId, String rawAccountNo,
                                        Account account, String birthDate,
                                        LocalDate startDate, LocalDate endDate) {
        JsonNode response = codefLoanTransactionClient.getPersonalTransactionList(
                bank.getOrganizationCode(), connectedId, rawAccountNo, null,
                startDate, endDate, birthDate
        );
        List<ParsedLoan> parsedResults = LoanTransactionResponseParser.parse(response.path("data"), account.getId());
        int transactionCount = 0;
        for (ParsedLoan parsed : parsedResults) {
            if (parsed.detail().getNextPaymentDate() == null) {
                parsed.detail().setNextPaymentDate(DefaultPaymentSchedule.nextAfter(endDate));
            }
            accountMapper.updateAccountDetails(parsed.detail());
            List<AccountTransaction> transactions = parsed.transactions();
            if (!transactions.isEmpty()) {
                accountTransactionMapper.insertAll(transactions);
                transactionCount += transactions.size();
            }
        }
        return transactionCount;
    }

    private int collectInstallmentSavings(PersonalBank bank, String connectedId, String rawAccountNo,
                                          Account account, String birthDate,
                                          LocalDate startDate, LocalDate endDate) {
        JsonNode response = codefInstallmentSavingsClient.getPersonalTransactionList(
                bank.getOrganizationCode(), connectedId, rawAccountNo, startDate, endDate, birthDate
        );
        List<ParsedInstallmentSavings> parsedResults = InstallmentSavingsResponseParser.parse(
                response.path("data"), account.getId()
        );
        LocalDate defaultNextPaymentDate = DefaultPaymentSchedule.nextAfter(endDate);
        if (parsedResults.isEmpty()) {
            updateNextPaymentDate(account.getId(), defaultNextPaymentDate);
        }
        int transactionCount = 0;
        for (ParsedInstallmentSavings parsed : parsedResults) {
            parsed.detail().setNextPaymentDate(defaultNextPaymentDate);
            accountMapper.updateAccountDetails(parsed.detail());
            List<AccountTransaction> transactions = parsed.transactions();
            if (!transactions.isEmpty()) {
                accountTransactionMapper.insertAll(transactions);
                transactionCount += transactions.size();
            }
        }
        return transactionCount;
    }

    private void updateNextPaymentDate(Long accountId, LocalDate nextPaymentDate) {
        Account schedule = new Account();
        schedule.setId(accountId);
        schedule.setNextPaymentDate(nextPaymentDate);
        accountMapper.updateAccountDetails(schedule);
    }

    private int collectTransactions(PersonalBank bank, String connectedId, String rawAccountNo, Long accountId,
                                    String birthDate, LocalDate startDate, LocalDate endDate) {
        boolean scBank = bank == PersonalBank.SC_BANK;
        JsonNode transactionListResponse;
        try {
            transactionListResponse = codefBankTransactionClient.getPersonalTransactionList(
                    bank.getOrganizationCode(), connectedId, rawAccountNo, startDate, endDate, birthDate, scBank
            );
        } catch (IllegalStateException e) {
            if (scBank && isCredentialRequiredMessage(e.getMessage())) {
                throw new CredentialRequiredException(
                        "SC은행 계좌가 비밀번호를 요구해 조회할 수 없습니다: accountId=" + accountId, e
                );
            }
            throw e;
        }
        List<AccountTransaction> transactions = AccountTransactionResponseParser.parse(
                transactionListResponse.path("data"), accountId
        );
        if (!transactions.isEmpty()) {
            accountTransactionMapper.insertAll(transactions);
        }
        return transactions.size();
    }

    /**
     * CODEF의 정확한 응답 코드가 확인되지 않아, 실패 메시지에 계좌 비밀번호 요구를 시사하는 표현이
     * 있을 때만 최선 추정한다(이슈 #158). {@code CodefConnectionClient.handleAccountFailure}의
     * birthDate 불일치 추정과 같은 방식이며, 공식 명세 확인 후 코드 기반 매핑으로 교체해야 한다.
     */
    private static boolean isCredentialRequiredMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("비밀번호")
                && (message.contains("필요") || message.contains("입력") || message.contains("누락"));
    }

    /** 수시입출 거래내역 API 대상은 예금/신탁 그룹 중 미분류(10)·수시입출(11) 계좌뿐이다. */
    private static boolean isOrdinaryTransactionEligible(Account account) {
        return account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST
                && (ORDINARY_DEPOSIT.equals(account.getDepositTypeCode())
                        || ORDINARY_WITHDRAWAL.equals(account.getDepositTypeCode()));
    }

    private static boolean isInstallmentSavingsEligible(Account account) {
        return account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST
                && REGULAR_SAVINGS.equals(account.getDepositTypeCode());
    }

    private static boolean isLoanEligible(Account account) {
        return account.getAccountGroup() == AccountGroup.LOAN || LOAN.equals(account.getDepositTypeCode());
    }
}
