package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.AccountLifecycleMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.AccountCollectionService;
import com.ntropy.account.service.VirtualAccountRegenerationService;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.common.dto.account.AccountRegistrationCommand;
import com.ntropy.common.exception.ServiceException;

class LocalFinancialAccountCommandClientTest {

    @Test
    void registersVirtualAccountViaRegenerationService() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        StubVirtualAccountRegenerationService regenerationService = new StubVirtualAccountRegenerationService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, regenerationService, new StubAccountLifecycleMapper(1, 1),
                new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null, null)
        );

        assertEquals("VIRTUAL", result.connectionType());
        assertEquals(3, result.accountCount());
        assertEquals(42L, regenerationService.lastUserId);
        assertEquals(PersonalBank.SHINHAN_BANK, regenerationService.lastBank);
        assertTrue(collectionService.registerAndCollectCalls == 0, "VIRTUAL 요청은 CODEF 수집을 호출하면 안 된다");
    }

    @Test
    void ignoresBirthDateForVirtualAccount() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null, "19900101")
        );

        assertEquals("VIRTUAL", result.connectionType());
        assertTrue(collectionService.registerAndCollectCalls == 0, "VIRTUAL 요청은 CODEF 수집을 호출하면 안 된다");
    }

    @Test
    void requiresCredentialsOnlyForCodef() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("CODEF", "0088", null, null, null)
                )
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void rejectsMissingBirthDateForRequiredBank() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id", "bank-password", null)
                )
        );

        assertEquals(AccountErrorCode.BIRTH_DATE_REQUIRED.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void rejectsInvalidBirthDateFormatForRequiredBank() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand(
                                "CODEF", "0003", "bank-id", "bank-password", "1990-01-01"
                        )
                )
        );

        assertEquals(AccountErrorCode.BIRTH_DATE_INVALID.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void registersKookminBankWithValidBirthDate() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id", "bank-password", "19900101")
        );

        assertEquals("CODEF", result.connectionType());
        assertEquals(1, collectionService.registerAndCollectCalls);
        assertEquals("19900101", collectionService.lastBirthDate);
    }

    @Test
    void registersIndustrialBankWithValidBirthDate() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0003", "bank-id", "bank-password", "19900101")
        );

        assertEquals(1, collectionService.registerAndCollectCalls);
        assertEquals("19900101", collectionService.lastBirthDate);
    }

    @Test
    void doesNotForwardBirthDateForBanksThatDoNotRequireIt() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", "19900101")
        );

        assertEquals(1, collectionService.registerAndCollectCalls);
        assertEquals(null, collectionService.lastBirthDate, "생년월일이 필요 없는 은행에는 전달하지 않는다");
    }

    @Test
    void myDataStatusUsesOnlyCodefConnection() {
        StubCodefConnectionMapper mapper = new StubCodefConnectionMapper();
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), mapper
        );

        assertFalse(client.findMyDataStatus(42L).connected());

        CodefConnection connection = new CodefConnection();
        connection.setUserId(42L);
        connection.setProvider(ConnectionProvider.CODEF.name());
        connection.setConnectedId("secret-connected-id");
        connection.setRegisteredInstitutionKeys("[\"0088\"]");
        mapper.connection = connection;

        var status = client.findMyDataStatus(42L);
        assertTrue(status.connected());
        assertEquals(List.of("0088"), status.connectedBanks().stream()
                .map(bank -> bank.organizationCode()).toList());
    }

    @Test
    void returnsSameNotFoundForDeactivationFailure() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(0, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class, () -> client.deactivateAccount(42L, 100L)
        );
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    void activatesAccountByFlippingStatusWithoutRegeneration() {
        StubVirtualAccountRegenerationService regenerationService = new StubVirtualAccountRegenerationService();
        StubAccountLifecycleMapper lifecycleMapper = new StubAccountLifecycleMapper(1, 1);
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), regenerationService,
                lifecycleMapper, new StubCodefConnectionMapper()
        );

        client.activateAccount(42L, 100L);

        assertEquals(100L, lifecycleMapper.lastActivatedAccountId);
        assertEquals(42L, lifecycleMapper.lastActivatedUserId);
        assertEquals(null, regenerationService.lastUserId, "활성화는 재생성을 호출하면 안 된다");
    }

    @Test
    void returnsSameNotFoundForActivationFailure() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 0), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class, () -> client.activateAccount(42L, 100L)
        );
        assertEquals(404, exception.getStatusCode());
    }

    private static LocalFinancialAccountCommandClient newClient(
            AccountCollectionService collectionService,
            VirtualAccountRegenerationService regenerationService,
            AccountLifecycleMapper lifecycleMapper,
            CodefConnectionMapper connectionMapper
    ) {
        return new LocalFinancialAccountCommandClient(
                collectionService, regenerationService, lifecycleMapper, connectionMapper
        );
    }

    private static class StubAccountCollectionService extends AccountCollectionService {
        private int registerAndCollectCalls = 0;
        private String lastBirthDate;

        StubAccountCollectionService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public List<Account> registerAndCollect(Long userId, PersonalBank bank, String loginId,
                                                String rawPassword, String birthDate,
                                                LocalDate transactionStartDate, LocalDate transactionEndDate) {
            registerAndCollectCalls++;
            lastBirthDate = birthDate;
            return List.of(new Account());
        }
    }

    private static class StubVirtualAccountRegenerationService extends VirtualAccountRegenerationService {
        private Long lastUserId;
        private PersonalBank lastBank;

        StubVirtualAccountRegenerationService() {
            super(null, null, null);
        }

        @Override
        public GenerationSummary regenerateForUser(Long userId, PersonalBank bank) {
            lastUserId = userId;
            lastBank = bank;
            return new GenerationSummary(1, 3, 2, 306, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        }
    }

    private static class StubAccountLifecycleMapper implements AccountLifecycleMapper {
        private final int deactivateResult;
        private final int activateResult;
        private Long lastActivatedAccountId;
        private Long lastActivatedUserId;

        StubAccountLifecycleMapper(int deactivateResult, int activateResult) {
            this.deactivateResult = deactivateResult;
            this.activateResult = activateResult;
        }

        @Override
        public int deactivateByIdAndUserId(Long id, Long userId) {
            return deactivateResult;
        }

        @Override
        public int activateByIdAndUserId(Long id, Long userId) {
            lastActivatedAccountId = id;
            lastActivatedUserId = userId;
            return activateResult;
        }
    }

    private static class StubCodefConnectionMapper implements CodefConnectionMapper {
        private CodefConnection connection;
        private int insertCalls = 0;

        @Override
        public void insert(CodefConnection codefConnection) {
            insertCalls++;
        }

        @Override
        public void insertIfAbsent(CodefConnection codefConnection) {
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            if (connection == null || !userId.equals(connection.getUserId())
                    || !provider.equals(connection.getProvider())) {
                return null;
            }
            return connection;
        }
    }
}
