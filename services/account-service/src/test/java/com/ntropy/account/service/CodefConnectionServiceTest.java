package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.ntropy.account.client.codef.CodefConnectionClient;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

class CodefConnectionServiceTest {

    @Test
    void registersAccountAndReadsSavedConnectionBack() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnectionService service = new CodefConnectionService(connectionClient, mapper);

        CodefConnection saved = service.registerAndSave(
                1L,
                "0004",
                "BK",
                "P",
                "sandbox-user",
                "sandbox-password",
                null
        );

        assertNotNull(saved);
        assertEquals(1L, saved.getUserId());
        assertEquals("connected-id", saved.getConnectedId());
        assertEquals(1, connectionClient.createCalls);
        assertEquals(0, connectionClient.addCalls);
    }

    @Test
    void addsAccountToExistingConnectedIdWithoutReplacingIt() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnection existing = new CodefConnection();
        existing.setUserId(1L);
        existing.setConnectedId("existing-connected-id");
        mapper.upsert(existing);
        CodefConnectionService service = new CodefConnectionService(connectionClient, mapper);

        CodefConnection saved = service.registerAndSave(
                1L, "0088", "BK", "P", "login-id", "password", null
        );

        assertEquals("existing-connected-id", saved.getConnectedId());
        assertEquals("existing-connected-id", connectionClient.connectedId);
        assertEquals("0088", connectionClient.organizationCode);
        assertEquals(0, connectionClient.createCalls);
        assertEquals(1, connectionClient.addCalls);
    }

    private static class StubCodefConnectionClient extends CodefConnectionClient {

        private int createCalls;
        private int addCalls;
        private String connectedId;
        private String organizationCode;

        StubCodefConnectionClient() {
            super(null, null);
        }

        @Override
        public String createConnection(String organizationCode, String businessType, String clientType,
                                       String loginId, String rawPassword, String birthDate) {
            createCalls++;
            this.organizationCode = organizationCode;
            return "connected-id";
        }

        @Override
        public void addConnection(String connectedId, String organizationCode,
                                  String businessType, String clientType,
                                  String loginId, String rawPassword, String birthDate) {
            addCalls++;
            this.connectedId = connectedId;
            this.organizationCode = organizationCode;
        }
    }

    private static class InMemoryCodefConnectionMapper implements CodefConnectionMapper {

        private CodefConnection connection;

        @Override
        public void insert(CodefConnection codefConnection) {
            this.connection = codefConnection;
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
            this.connection = codefConnection;
        }

        @Override
        public CodefConnection findByUserId(Long userId) {
            return connection != null && userId.equals(connection.getUserId()) ? connection : null;
        }
    }
}
