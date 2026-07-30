package com.ntropy.account.service;

import org.springframework.stereotype.Service;

import com.ntropy.account.client.codef.CodefConnectionClient;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 계정을 등록하고 발급된 connectedId를 사용자와 연결한다.
 */
@Service
@RequiredArgsConstructor
public class CodefConnectionService {

    private final CodefConnectionClient codefConnectionClient;
    private final CodefConnectionMapper codefConnectionMapper;

    public CodefConnection registerAndSave(Long userId, String organizationCode,
                                           String businessType, String clientType,
                                           String loginId, String rawPassword, String birthDate) {
        CodefConnection existing = codefConnectionMapper.findByUserId(userId);
        if (existing != null && existing.getConnectedId() != null
                && !existing.getConnectedId().isBlank()) {
            codefConnectionClient.addConnection(
                    existing.getConnectedId(),
                    organizationCode,
                    businessType,
                    clientType,
                    loginId,
                    rawPassword,
                    birthDate
            );
            return existing;
        }

        String connectedId = codefConnectionClient.createConnection(
                organizationCode, businessType, clientType, loginId, rawPassword, birthDate
        );

        CodefConnection connection = new CodefConnection();
        connection.setUserId(userId);
        connection.setConnectedId(connectedId);
        codefConnectionMapper.upsert(connection);

        CodefConnection saved = codefConnectionMapper.findByUserId(userId);
        if (saved == null || !connectedId.equals(saved.getConnectedId())) {
            throw new IllegalStateException("CODEF connectedId 저장 확인 실패");
        }
        return saved;
    }
}
