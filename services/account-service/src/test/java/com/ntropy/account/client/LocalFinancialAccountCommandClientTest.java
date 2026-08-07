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
import com.ntropy.common.client.UserBirthDateQueryClient;
import com.ntropy.common.dto.account.AccountRegistrationCommand;
import com.ntropy.common.exception.ServiceException;

class LocalFinancialAccountCommandClientTest {

    @Test
    void blocksVirtualRegistrationDuringRefactoring() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        StubCodefConnectionMapper connectionMapper = new StubCodefConnectionMapper();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, (id, userId) -> 1, connectionMapper, emptyBirthDateProvider()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null)
                )
        );

        assertEquals(503, exception.getStatusCode());
        assertTrue(collectionService.registerAndCollectCalls == 0);
        assertTrue(connectionMapper.insertCalls == 0);
    }

    @Test
    void requiresCredentialsOnlyForCodef() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(),
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
                new StubAccountCollectionService(),
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
                new StubAccountCollectionService(),
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
                new StubAccountCollectionService(),
                (id, userId) -> 0, new StubCodefConnectionMapper(), emptyBirthDateProvider()
        );

        ServiceException exception = assertThrows(
                ServiceException.class, () -> client.deactivateAccount(42L, 100L)
        );
        assertEquals(404, exception.getStatusCode());
    }

    private static LocalFinancialAccountCommandClient newClient(
            AccountCollectionService collectionService,
            AccountLifecycleMapper lifecycleMapper,
            CodefConnectionMapper connectionMapper,
            org.springframework.beans.factory.ObjectProvider<UserBirthDateQueryClient> birthDateProvider
    ) {
        return new LocalFinancialAccountCommandClient(
                collectionService, lifecycleMapper, connectionMapper, birthDateProvider
        );
    }

    private static org.springframework.beans.factory.ObjectProvider<UserBirthDateQueryClient> emptyBirthDateProvider() {
        return new DefaultListableBeanFactory().getBeanProvider(UserBirthDateQueryClient.class);
    }

    private static class StubAccountCollectionService extends AccountCollectionService {
        private int registerAndCollectCalls = 0;

        StubAccountCollectionService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public List<Account> registerAndCollect(Long userId, PersonalBank bank, String loginId,
                                                String rawPassword, String birthDate,
                                                LocalDate transactionStartDate, LocalDate transactionEndDate) {
            registerAndCollectCalls++;
            return List.of(new Account());
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
