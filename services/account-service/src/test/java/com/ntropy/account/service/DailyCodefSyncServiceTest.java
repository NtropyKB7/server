package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.config.BirthDateEncryptionProperties;
import com.ntropy.account.config.IncrementalSyncPolicy;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountSyncState;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountSyncStateMapper;
import com.ntropy.account.mapper.AccountTransactionMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;
import com.ntropy.account.service.AccountCollectionService.AccountCollectionOutcome;
import com.ntropy.account.service.BatchExecutionLeaseService.LeaseHandle;
import com.ntropy.common.dto.account.DailyFinancialSyncResult;

class DailyCodefSyncServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 8, 14, 3, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);
    private static final LeaseHandle LEASE = new LeaseHandle(1L, "daily-sync-codef", BUSINESS_DATE, "owner-a", "token-a");

    @Test
    void marksUserSuccessfulAndAdvancesWatermarkWhenAllAccountsSucceed() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.SUCCESS, 5)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(List.of(1L), result.successfulUserIds());
        assertTrue(result.partialFailedUserIds().isEmpty());
        assertEquals("SUCCESS", result.executionStatus());
        assertEquals(5L, result.processedTransactionCount());
        assertEquals(1, syncStateMapper.advanceCalls.size());
        assertTrue(result.affectedYearMonthsByUser().containsKey(1L));
    }

    @Test
    void doesNotAdvanceWatermarkAndMarksUserPartialFailedOnRealFailure() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.FAILED, 0)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertTrue(result.successfulUserIds().isEmpty());
        assertEquals(List.of(1L), result.partialFailedUserIds());
        assertEquals("PARTIAL_FAILED", result.executionStatus());
        assertTrue(syncStateMapper.advanceCalls.isEmpty(), "실패한 기관의 watermark는 전진하면 안 됩니다");
    }

    @Test
    void advancesWatermarkButDoesNotCountAsSuccessWhenOnlySkippedCredentialRequired() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0023\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SC_BANK, List.of(
                outcome(AccountCollectionOutcome.Status.SKIPPED_CREDENTIAL_REQUIRED, 0)
        ));
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(1, syncStateMapper.advanceCalls.size(), "SKIPPED_CREDENTIAL_REQUIRED만 있어도 watermark는 전진해야 합니다");
        assertTrue(result.successfulUserIds().isEmpty(), "실제로 수집된 게 없으면 성공 사용자로 집계하지 않습니다");
        assertTrue(result.partialFailedUserIds().isEmpty());
    }

    @Test
    void stopsProcessingImmediatelyWhenHeartbeatFailsAndReportsFailedExecution() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0088\",\"0011\"]", null, null));
        connectionMapper.put(2L, connection(2L, 2L, "cid2", "[\"0088\"]", null, null));
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.stub(1L, PersonalBank.SHINHAN_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        collectionService.stub(1L, PersonalBank.NH_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        collectionService.stub(2L, PersonalBank.SHINHAN_BANK, List.of(outcome(AccountCollectionOutcome.Status.SUCCESS, 1)));
        CountingLeaseService leaseService = new CountingLeaseService(2); // 2번째 heartbeat부터 실패
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, leaseService);

        DailyFinancialSyncResult result = service.synchronize(List.of(1L, 2L), BUSINESS_DATE, LEASE);

        assertEquals("FAILED", result.executionStatus());
        assertEquals(2, leaseService.heartbeatCalls, "두 번째 heartbeat 실패 직후 멈춰야 합니다");
    }

    @Test
    void marksInstitutionFailedWhenEncryptedBirthDateIsMissingForRequiredBank() {
        FakeCodefConnectionMapper connectionMapper = new FakeCodefConnectionMapper();
        connectionMapper.put(1L, connection(1L, 1L, "cid", "[\"0004\"]", null, null)); // KB인데 birthDate 없음
        FakeAccountSyncStateMapper syncStateMapper = new FakeAccountSyncStateMapper();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        DailyCodefSyncService service = newService(connectionMapper, syncStateMapper, collectionService, alwaysTrueLease());

        DailyFinancialSyncResult result = service.synchronize(List.of(1L), BUSINESS_DATE, LEASE);

        assertEquals(List.of(1L), result.partialFailedUserIds());
        assertTrue(syncStateMapper.advanceCalls.isEmpty());
        assertEquals(1, result.institutionResults().size());
        assertEquals("CREDENTIAL_MISSING", result.institutionResults().get(0).errorCode());
    }

    private static DailyCodefSyncService newService(CodefConnectionMapper connectionMapper,
                                                     AccountSyncStateMapper syncStateMapper,
                                                     AccountCollectionService collectionService,
                                                     BatchExecutionLeaseService leaseService) {
        BirthDateCipher cipher = new BirthDateCipher(
                new BirthDateEncryptionProperties("EnZnHF6ULrhAjwHSJs5+2lizbiv7BHiB+5sZ4YIKEmc=", 1)
        );
        return new DailyCodefSyncService(
                connectionMapper, syncStateMapper, new NoopAccountTransactionMapper(), collectionService,
                leaseService, cipher, new IncrementalSyncPolicy(1, 90), CLOCK
        );
    }

    private static BatchExecutionLeaseService alwaysTrueLease() {
        return new CountingLeaseService(Integer.MAX_VALUE);
    }

    private static AccountCollectionOutcome outcome(AccountCollectionOutcome.Status status, int transactionCount) {
        Account account = new Account();
        account.setId(100L);
        return new AccountCollectionOutcome(account, status, status == AccountCollectionOutcome.Status.FAILED
                ? "COLLECTION_FAILED" : null, transactionCount);
    }

    private static CodefConnection connection(Long id, Long userId, String connectedId, String registeredKeys,
                                              String birthDateCiphertext, String birthDateIv) {
        CodefConnection connection = new CodefConnection();
        connection.setId(id);
        connection.setUserId(userId);
        connection.setProvider("CODEF");
        connection.setConnectedId(connectedId);
        connection.setRegisteredInstitutionKeys(registeredKeys);
        connection.setBirthDateCiphertext(birthDateCiphertext);
        connection.setBirthDateIv(birthDateIv);
        connection.setBirthDateKeyVersion(birthDateCiphertext == null ? null : 1);
        return connection;
    }

    /** heartbeat를 callLimit번째 호출부터 실패시킨다(1-based). Integer.MAX_VALUE면 항상 성공. */
    private static class CountingLeaseService extends BatchExecutionLeaseService {

        private final int failFromCall;
        private int heartbeatCalls;

        CountingLeaseService(int failFromCall) {
            super(null, null, null);
            this.failFromCall = failFromCall;
        }

        @Override
        public boolean heartbeat(LeaseHandle lease) {
            heartbeatCalls++;
            return heartbeatCalls < failFromCall;
        }
    }

    private static class StubAccountCollectionService extends AccountCollectionService {

        private final Map<String, List<AccountCollectionOutcome>> byKey = new HashMap<>();

        StubAccountCollectionService() {
            super(null, null, null, null, null, null, null);
        }

        void stub(Long userId, PersonalBank bank, List<AccountCollectionOutcome> outcomes) {
            byKey.put(userId + ":" + bank.getOrganizationCode(), outcomes);
        }

        @Override
        public List<AccountCollectionOutcome> collectForDailySync(Long userId, PersonalBank bank, String birthDate,
                                                                   LocalDate transactionStartDate,
                                                                   LocalDate transactionEndDate) {
            return byKey.getOrDefault(userId + ":" + bank.getOrganizationCode(), List.of());
        }
    }

    private static class FakeCodefConnectionMapper implements CodefConnectionMapper {

        private final Map<Long, CodefConnection> byUserId = new HashMap<>();

        void put(Long userId, CodefConnection connection) {
            byUserId.put(userId, connection);
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
            return byUserId.get(userId);
        }
    }

    private static class FakeAccountSyncStateMapper implements AccountSyncStateMapper {

        private final List<Object[]> advanceCalls = new ArrayList<>();

        @Override
        public AccountSyncState findByConnectionAndOrganization(Long codefConnectionId, String organizationCode) {
            return null;
        }

        @Override
        public void insertIfAbsent(AccountSyncState state) {
        }

        @Override
        public int advanceIfOwner(Long codefConnectionId, String organizationCode,
                                  LocalDateTime lastSuccessfulSyncedAt, String lastStatus, String lastErrorCode,
                                  String jobName, LocalDate businessDate, String ownerId, String leaseToken,
                                  LocalDateTime now) {
            advanceCalls.add(new Object[]{codefConnectionId, organizationCode, lastStatus});
            return 1;
        }
    }

    private static class NoopAccountTransactionMapper implements AccountTransactionMapper {

        @Override
        public void insertAll(List<com.ntropy.account.domain.entity.AccountTransaction> transactions) {
        }

        @Override
        public List<com.ntropy.account.domain.entity.AccountTransaction> findByAccountIdAndDateRange(
                Long accountId, LocalDate startDate, LocalDate endDate) {
            return List.of();
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
        }

        @Override
        public LocalDate findMostRecentTransactionDate(Long codefConnectionId, String organizationCode) {
            return null;
        }
    }
}
