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
        CodefConnectionClient connectionClient = new CodefConnectionClient(null, null) {
            @Override
            public String createConnection(String organizationCode, String businessType, String clientType,
                                           String loginId, String rawPassword, String birthDate) {
                return "connected-id";
            }
        };
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
