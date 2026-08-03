package com.ntropy.account.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.account.client.codef.CodefConnectionClient;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 계정을 등록하고 발급된 connectedId를 사용자와 연결한다.
 * 같은 사용자가 이미 등록한 기관을 다시 요청하면 CODEF {@code /account/add} 호출 자체를 생략해
 * 순차적인 중복 등록을 막는다 (동시 요청 경합 방지는 후속 이슈 범위).
 */
@Service
@RequiredArgsConstructor
public class CodefConnectionService {

    private final CodefConnectionClient codefConnectionClient;
    private final CodefConnectionMapper codefConnectionMapper;

    public CodefConnection registerAndSave(Long userId, String organizationCode,
                                           String businessType, String clientType,
                                           String loginId, String rawPassword, String birthDate) {
        CodefConnection existing = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (existing != null && existing.getConnectedId() != null
                && !existing.getConnectedId().isBlank()) {
            List<String> registeredKeys = InstitutionKeys.parse(existing.getRegisteredInstitutionKeys());
            if (registeredKeys.contains(organizationCode)) {
                return existing;
            }

            codefConnectionClient.addConnection(
                    existing.getConnectedId(),
                    organizationCode,
                    businessType,
                    clientType,
                    loginId,
                    rawPassword,
                    birthDate
            );

            registeredKeys.add(organizationCode);
            existing.setRegisteredInstitutionKeys(InstitutionKeys.serialize(registeredKeys));
            codefConnectionMapper.upsert(existing);
            return codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        }

        String connectedId = codefConnectionClient.createConnection(
                organizationCode, businessType, clientType, loginId, rawPassword, birthDate
        );

        CodefConnection connection = new CodefConnection();
        connection.setUserId(userId);
        connection.setProvider(ConnectionProvider.CODEF.name());
        connection.setConnectedId(connectedId);
        connection.setRegisteredInstitutionKeys(InstitutionKeys.serialize(List.of(organizationCode)));
        codefConnectionMapper.upsert(connection);

        CodefConnection saved = codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.CODEF.name());
        if (saved == null || !connectedId.equals(saved.getConnectedId())) {
            throw new IllegalStateException("CODEF connectedId 저장 확인 실패");
        }
        return saved;
    }
}
