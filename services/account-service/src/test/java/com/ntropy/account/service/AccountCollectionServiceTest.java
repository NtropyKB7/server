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
                    "resAfterTranBalance": "10000"
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
                personalBankAccountService, connectionMapper, transactionClient, accountMapper, transactionMapper
        );

        List<Account> savedAccounts = service.collect(
                1L, PersonalBank.SHINHAN_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertEquals(2, savedAccounts.size());
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
                personalBankAccountService, connectionMapper, transactionClient, accountMapper, transactionMapper
        );

        service.collect(
                1L, PersonalBank.SC_BANK, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)
        );

        assertTrue(transactionClient.calls.isEmpty());
        assertEquals(0, transactionMapper.insertedBatches);
    }

    @Test
    void rejectsCollectWhenConnectionDoesNotExist() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper(null, null);
        AccountCollectionService service = new AccountCollectionService(
                null, connectionMapper, null, null, null
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
                personalBankAccountService, connectionMapper, transactionClient, accountMapper, transactionMapper
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
        public void upsert(CodefConnection codefConnection) {
        }

        @Override
        public CodefConnection findByUserId(Long userId) {
            if (connectedId == null) {
                return null;
            }
            CodefConnection connection = new CodefConnection();
            connection.setId(connectionId);
            connection.setUserId(userId);
            connection.setConnectedId(connectedId);
            return connection;
        }
    }

    private static class FakeAccountMapper implements AccountMapper {

        private final Map<String, Account> store = new HashMap<>();
        private long nextId = 1;

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
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return store.get(codefConnectionId + ":" + accountNoHash);
        }
    }

    private static class FakeAccountTransactionMapper implements AccountTransactionMapper {

        private int insertedBatches;

        @Override
        public void insertAll(List<AccountTransaction> transactions) {
            insertedBatches++;
        }
    }
}
