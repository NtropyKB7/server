package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.AccountLifecycleMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.AccountCollectionService;
import com.ntropy.account.service.VirtualFinancialDataService;
import com.ntropy.common.client.UserBirthDateQueryClient;
import com.ntropy.common.dto.account.AccountRegistrationCommand;
import com.ntropy.common.exception.ServiceException;

class LocalFinancialAccountCommandClientTest {

    @Test
    void registersVirtualAccountWithoutBankCredentials() {
        StubVirtualFinancialDataService virtualService = new StubVirtualFinancialDataService();
        LocalFinancialAccountCommandClient client = newClient(
                virtualService, new StubAccountCollectionService(), (id, userId) -> 1,
                new StubCodefConnectionMapper(), emptyBirthDateProvider()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null)
        );

        assertEquals("VIRTUAL", result.connectionType());
        assertEquals(2, result.accountCount());
        assertEquals(42L, virtualService.userId);
        assertEquals(PersonalBank.SHINHAN_BANK, virtualService.bank);
    }

    @Test
    void requiresCredentialsOnlyForCodef() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubVirtualFinancialDataService(), new StubAccountCollectionService(),
                (id, userId) -> 1, new StubCodefConnectionMapper(), emptyBirthDateProvider()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("CODEF", "0088", null, null)
                )
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void requiresMemberBirthDateIntegrationForBirthDateBank() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubVirtualFinancialDataService(), new StubAccountCollectionService(),
                (id, userId) -> 1, new StubCodefConnectionMapper(), emptyBirthDateProvider()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id", "bank-password")
                )
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void myDataStatusUsesOnlyCodefConnection() {
        StubCodefConnectionMapper mapper = new StubCodefConnectionMapper();
        LocalFinancialAccountCommandClient client = newClient(
                new StubVirtualFinancialDataService(), new StubAccountCollectionService(),
                (id, userId) -> 1, mapper, emptyBirthDateProvider()
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
                new StubVirtualFinancialDataService(), new StubAccountCollectionService(),
                (id, userId) -> 0, new StubCodefConnectionMapper(), emptyBirthDateProvider()
        );

        ServiceException exception = assertThrows(
                ServiceException.class, () -> client.deactivateAccount(42L, 100L)
        );
        assertEquals(404, exception.getStatusCode());
    }

    private static LocalFinancialAccountCommandClient newClient(
            VirtualFinancialDataService virtualService,
            AccountCollectionService collectionService,
            AccountLifecycleMapper lifecycleMapper,
            CodefConnectionMapper connectionMapper,
            org.springframework.beans.factory.ObjectProvider<UserBirthDateQueryClient> birthDateProvider
    ) {
        return new LocalFinancialAccountCommandClient(
                virtualService, collectionService, lifecycleMapper, connectionMapper, birthDateProvider
        );
    }

    private static org.springframework.beans.factory.ObjectProvider<UserBirthDateQueryClient> emptyBirthDateProvider() {
        return new DefaultListableBeanFactory().getBeanProvider(UserBirthDateQueryClient.class);
    }

    private static class StubVirtualFinancialDataService extends VirtualFinancialDataService {
        private Long userId;
        private PersonalBank bank;

        StubVirtualFinancialDataService() {
            super(null, null, null, null);
        }

        @Override
        public GenerationSummary generateForUser(Long userId, PersonalBank bank) {
            this.userId = userId;
            this.bank = bank;
            return new GenerationSummary(
                    1, 2, 2, 300, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)
            );
        }
    }

    private static class StubAccountCollectionService extends AccountCollectionService {
        StubAccountCollectionService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public List<Account> registerAndCollect(Long userId, PersonalBank bank, String loginId,
                                                String rawPassword, String birthDate,
                                                LocalDate transactionStartDate, LocalDate transactionEndDate) {
            return List.of(new Account());
        }
    }

    private static class StubCodefConnectionMapper implements CodefConnectionMapper {
        private CodefConnection connection;

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
            if (connection == null || !userId.equals(connection.getUserId())
                    || !provider.equals(connection.getProvider())) {
                return null;
            }
            return connection;
        }
    }
}
