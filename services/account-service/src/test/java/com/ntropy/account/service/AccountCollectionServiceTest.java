package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.CodefBankTransactionClient;
import com.ntropy.account.client.codef.CodefInstallmentSavingsClient;
import com.ntropy.account.client.codef.CodefLoanTransactionClient;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;

class AccountCollectionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ACCOUNT_LIST_WITH_ORDINARY_AND_FUND = """
            {
              "result": {"code": "CF-00000"},
              "data": {
                "resDepositTrust": [
                  {
                    "resAccount": "110123456789",
                    "resAccountDisplay": "110-***-456789",
                    "resAccountDeposit": "11",
                    "resAccountBalance": "10000",
                    "resAccountCurrency": "KRW"
                  }
                ],
                "resFund": [
                  {
                    "resAccount": "998877",
                    "resAccountDisplay": "998-***-877",
                    "resAccountDeposit": "30",
                    "resAccountBalance": "50000",
                    "resAccountCurrency": "KRW"
                  }
                ]
              }
            }
            """;

    // 계좌 하나만 조회하는 실제 DEMO 응답은 data가 배열이 아니라 계좌 요약 객체 하나다 (신한 DEMO로 확인).
    private static final String TRANSACTION_LIST_WITH_ONE_ENTRY = """
            {
              "result": {"code": "CF-00000"},
              "data": {
                "resAccount": "110123456789",
                "resTrHistoryList": [
                  {
                    "resAccountTrDate": "20260110",
                    "resAccountOut": "0",
                    "resAccountIn": "10000",
                    "resAfterTranBalance": "10000",
                    "resAccountDesc3": "쿠팡 이츠"
                  }
                ]
              }
            }
            """;

    private static final String ACCOUNT_LIST_WITH_INSTALLMENT_SAVINGS = """
            {
              "result": {"code": "CF-00000"},
              "data": {
                "resDepositTrust": [
                  {
                    "resAccount": "302123456789",
                    "resAccountDeposit": "12",
                    "resAccountBalance": "1300000",
                    "resAccountCurrency": "KRW"
                  }
                ]
              }
            }
            """;

    private static final String INSTALLMENT_SAVINGS_WITH_ONE_ENTRY = """
            {
              "result": {"code": "CF-00000"},
              "data": {
                "resAccountStatus": "정상",
                "resTrHistoryList": [
                  {
                    "resRoundNo": "13",
                    "resAccountTrDate": "20260105",
                    "resAccountIn": "100000",
                    "resAfterTranBalance": "1300000"
                  }
                ]
              }
            }
            """;

    private static final String ACCOUNT_LIST_WITH_LOAN = """
            {
              "result": {"code": "CF-00000"},
              "data": {
                "resLoan": [
                  {
                    "resAccount": "302987654321",
                    "resAccountDeposit": "40",
                    "resAccountBalance": "95000000",
                    "resAccountCurrency": "KRW",
                    "resAccountLoanExecNo": "execution-1"
                  }
                ]
              }
            }
            """;

    private static final String LOAN_WITH_ONE_ENTRY = """
            {
              "result": {"code": "CF-00000"},
              "data": {
                "resPrincipal": "120000000",
                "resLoanBalance": "95000000",
                "resTrHistoryList": [
                  {
                    "resAccountTrDate": "20260105",
                    "resTransTypeNm": "원리금상환",
                    "resPrincipal": "900000",
                    "resInterest": "300000",
                    "resLoanBalance": "95000000"
                  }
                ]
              }
            }
            """;

    @Test
    void collectsTransactionsOnlyForOrdinaryDepositAccounts() throws Exception {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(1L, "connected-id");
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        FakeAccountTransactionMapper transactionMapper = new FakeAccountTransactionMapper();
        FakeCodefBankTransactionClient transactionClient = new FakeCodefBankTransactionClient(
                objectMapper.readTree(TRANSACTION_LIST_WITH_ONE_ENTRY)
        );
        StubPersonalBankAccountService personalBankAccountService = new StubPersonalBankAccountService(
                objectMapper.readTree(ACCOUNT_LIST_WITH_ORDINARY_AND_FUND)
        );
        AccountCollectionService service = new AccountCollectionService(
                personalBankAccountService, connectionMapper, transactionClient, null, null,
                accountMapper, transactionMapper
        );

        List<Account> savedAccounts = service.collect(
                1L, PersonalBank.SHINHAN_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        // resFund 영역은 전용 ACCOUNT 행을 만들지 않고 파싱 단계에서 건너뛴다.
        assertEquals(1, savedAccounts.size());
        assertEquals(1, transactionClient.calls.size());
        assertEquals("110123456789", transactionClient.calls.get(0).account());
        assertEquals(1, transactionMapper.insertedBatches);
    }

    @Test
    void skipsTransactionCollectionForScBank() throws Exception {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(1L, "connected-id");
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        FakeAccountTransactionMapper transactionMapper = new FakeAccountTransactionMapper();
        FakeCodefBankTransactionClient transactionClient = new FakeCodefBankTransactionClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        StubPersonalBankAccountService personalBankAccountService = new StubPersonalBankAccountService(
                objectMapper.readTree(ACCOUNT_LIST_WITH_ORDINARY_AND_FUND)
        );
        AccountCollectionService service = new AccountCollectionService(
                personalBankAccountService, connectionMapper, transactionClient, null, null,
                accountMapper, transactionMapper
        );

        service.collect(
                1L, PersonalBank.SC_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertTrue(transactionClient.calls.isEmpty());
        assertEquals(0, transactionMapper.insertedBatches);
    }

    @Test
    void collectsInstallmentSavingsForDepositType12() throws Exception {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(1L, "connected-id");
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        FakeAccountTransactionMapper transactionMapper = new FakeAccountTransactionMapper();
        FakeCodefBankTransactionClient transactionClient = new FakeCodefBankTransactionClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        FakeCodefInstallmentSavingsClient installmentClient = new FakeCodefInstallmentSavingsClient(
                objectMapper.readTree(INSTALLMENT_SAVINGS_WITH_ONE_ENTRY)
        );
        StubPersonalBankAccountService personalBankAccountService = new StubPersonalBankAccountService(
                objectMapper.readTree(ACCOUNT_LIST_WITH_INSTALLMENT_SAVINGS)
        );
        AccountCollectionService service = new AccountCollectionService(
                personalBankAccountService, connectionMapper, transactionClient, installmentClient, null,
                accountMapper, transactionMapper
        );

        service.collect(
                1L, PersonalBank.NH_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertTrue(transactionClient.calls.isEmpty());
        assertEquals(1, installmentClient.calls.size());
        assertEquals("302123456789", installmentClient.calls.get(0).account());
        assertEquals(1, accountMapper.updatedAccountDetails);
        assertEquals(1, transactionMapper.insertedTransactions.size());
    }

    @Test
    void doesNotCollectInstallmentSavingsForUnclassifiedDepositType14() throws Exception {
        FakeCodefInstallmentSavingsClient installmentClient = new FakeCodefInstallmentSavingsClient(
                objectMapper.readTree(INSTALLMENT_SAVINGS_WITH_ONE_ENTRY)
        );
        AccountCollectionService service = new AccountCollectionService(
                new StubPersonalBankAccountService(objectMapper.readTree(
                        ACCOUNT_LIST_WITH_INSTALLMENT_SAVINGS.replace("\"12\"", "\"14\"")
                )),
                new FakeCodefConnectionMapper(1L, "connected-id"),
                new FakeCodefBankTransactionClient(objectMapper.readTree("{}")),
                installmentClient,
                null,
                new FakeAccountMapper(),
                new FakeAccountTransactionMapper()
        );

        service.collect(
                1L, PersonalBank.NH_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertTrue(installmentClient.calls.isEmpty());
    }

    @Test
    void collectsLoanTransactionsWithoutUnusedExecutionNumber() throws Exception {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(1L, "connected-id");
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        FakeAccountTransactionMapper transactionMapper = new FakeAccountTransactionMapper();
        FakeCodefBankTransactionClient transactionClient = new FakeCodefBankTransactionClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        FakeCodefLoanTransactionClient loanClient = new FakeCodefLoanTransactionClient(
                objectMapper.readTree(LOAN_WITH_ONE_ENTRY)
        );
        StubPersonalBankAccountService personalBankAccountService = new StubPersonalBankAccountService(
                objectMapper.readTree(ACCOUNT_LIST_WITH_LOAN)
        );
        AccountCollectionService service = new AccountCollectionService(
                personalBankAccountService, connectionMapper, transactionClient, null, loanClient,
                accountMapper, transactionMapper
        );

        service.collect(
                1L, PersonalBank.NH_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertTrue(transactionClient.calls.isEmpty());
        assertEquals(1, loanClient.calls.size());
        assertEquals("302987654321", loanClient.calls.get(0).account());
        assertEquals(null, loanClient.calls.get(0).loanExecutionNumber());
        assertEquals(1, accountMapper.updatedAccountDetails);
        assertEquals(1, transactionMapper.insertedTransactions.size());
    }

    @Test
    void rejectsCollectWhenConnectionDoesNotExist() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(null, null);
        AccountCollectionService service = new AccountCollectionService(
                null, connectionMapper, null, null, null, null, null
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.collect(
                        1L, PersonalBank.SHINHAN_BANK, null,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
                )
        );
    }

    @Test
    void registerAndCollectRegistersBeforeCollecting() throws Exception {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(1L, "connected-id");
        FakeAccountMapper accountMapper = new FakeAccountMapper();
        FakeAccountTransactionMapper transactionMapper = new FakeAccountTransactionMapper();
        FakeCodefBankTransactionClient transactionClient = new FakeCodefBankTransactionClient(
                objectMapper.readTree("{\"result\":{\"code\":\"CF-00000\"},\"data\":[]}")
        );
        StubPersonalBankAccountService personalBankAccountService = new StubPersonalBankAccountService(
                objectMapper.readTree(ACCOUNT_LIST_WITH_ORDINARY_AND_FUND)
        );
        AccountCollectionService service = new AccountCollectionService(
                personalBankAccountService, connectionMapper, transactionClient, null, null,
                accountMapper, transactionMapper
        );

        service.registerAndCollect(
                1L, PersonalBank.SHINHAN_BANK, "login-id", "password", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertEquals(1, personalBankAccountService.registerCalls);
    }

    private record TransactionCall(String organizationCode, String connectedId, String account) {
    }

    private static class FakeCodefBankTransactionClient extends CodefBankTransactionClient {

        private final JsonNode response;
        private final List<TransactionCall> calls = new ArrayList<>();

        FakeCodefBankTransactionClient(JsonNode response) {
            super(null);
            this.response = response;
        }

        @Override
        public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                                   LocalDate startDate, LocalDate endDate, String birthDate) {
            calls.add(new TransactionCall(organizationCode, connectedId, account));
            return response;
        }
    }

    private static class FakeCodefInstallmentSavingsClient extends CodefInstallmentSavingsClient {

        private final JsonNode response;
        private final List<TransactionCall> calls = new ArrayList<>();

        FakeCodefInstallmentSavingsClient(JsonNode response) {
            super(null);
            this.response = response;
        }

        @Override
        public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                                   LocalDate startDate, LocalDate endDate, String birthDate) {
            calls.add(new TransactionCall(organizationCode, connectedId, account));
            return response;
        }
    }

    private record LoanTransactionCall(String organizationCode, String connectedId, String account,
                                       String loanExecutionNumber) {
    }

    private static class FakeCodefLoanTransactionClient extends CodefLoanTransactionClient {

        private final JsonNode response;
        private final List<LoanTransactionCall> calls = new ArrayList<>();

        FakeCodefLoanTransactionClient(JsonNode response) {
            super(null);
            this.response = response;
        }

        @Override
        public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                                   String accountLoanExecNo, LocalDate startDate,
                                                   LocalDate endDate, String birthDate) {
            calls.add(new LoanTransactionCall(organizationCode, connectedId, account, accountLoanExecNo));
            return response;
        }
    }

    private static class StubPersonalBankAccountService extends PersonalBankAccountService {

        private final JsonNode accountListResponse;
        private int registerCalls;

        StubPersonalBankAccountService(JsonNode accountListResponse) {
            super(null, null, null);
            this.accountListResponse = accountListResponse;
        }

        @Override
        public CodefConnection registerPersonalAccount(Long userId, PersonalBank bank,
                                                        String loginId, String rawPassword, String birthDate) {
            registerCalls++;
            return null;
        }

        @Override
        public JsonNode getPersonalAccountList(Long userId, PersonalBank bank) {
            return accountListResponse;
        }
    }

    private static class FakeCodefConnectionMapper implements CodefConnectionMapper {

        private final Long connectionId;
        private final String connectedId;

        FakeCodefConnectionMapper(Long connectionId, String connectedId) {
            this.connectionId = connectionId;
            this.connectedId = connectedId;
        }

        @Override
        public void insert(CodefConnection codefConnection) {
        }

        @Override
        public void insertIfAbsent(CodefConnection codefConnection) {
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            if (connectedId == null) {
                return null;
            }
            CodefConnection connection = new CodefConnection();
            connection.setId(connectionId);
            connection.setUserId(userId);
            connection.setProvider(provider);
            connection.setConnectedId(connectedId);
            return connection;
        }
    }

    private static class FakeAccountMapper implements AccountMapper {

        private final Map<String, Account> store = new HashMap<>();
        private long nextId = 1;
        private int updatedAccountDetails;

        @Override
        public void upsert(Account account) {
            String key = account.getCodefConnectionId() + ":" + account.getAccountNoHash();
            if (!store.containsKey(key)) {
                account.setId(nextId++);
            } else {
                account.setId(store.get(key).getId());
            }
            store.put(key, account);
        }

        @Override
        public void updateAccountDetails(Account account) {
            updatedAccountDetails++;
            store.values().stream()
                    .filter(existing -> existing.getId().equals(account.getId()))
                    .findFirst()
                    .ifPresent(existing -> existing.setNextPaymentDate(account.getNextPaymentDate()));
        }

        @Override
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return store.get(codefConnectionId + ":" + accountNoHash);
        }

        @Override
        public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) {
            return store.values().stream()
                    .filter(a -> id.equals(a.getId()) && userId.equals(a.getUserId()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream().filter(a -> userId.equals(a.getUserId())).toList();
        }
    }

    private static class FakeAccountTransactionMapper implements AccountTransactionMapper {

        private int insertedBatches;
        private final List<AccountTransaction> insertedTransactions = new ArrayList<>();

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            insertedBatches++;
            insertedTransactions.addAll(transactions);
        }

        @Override
        public List<AccountTransaction> findByAccountIdAndDateRange(Long accountId, LocalDate startDate,
                                                                    LocalDate endDate) {
            return insertedTransactions.stream()
                    .filter(transaction -> accountId.equals(transaction.getAccountId()))
                    .filter(transaction -> !transaction.getTranDate().isBefore(startDate)
                            && !transaction.getTranDate().isAfter(endDate))
                    .toList();
        }

    }
}
