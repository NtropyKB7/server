package com.ntropy.account.adapter.user;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.port.user.SeededVirtualUser;
import com.ntropy.account.port.user.SeededVirtualUserBatch;
import com.ntropy.account.port.user.UserPort;
import com.ntropy.account.port.user.VirtualDatasetExecutionContext;
import com.ntropy.common.client.ActiveUserQueryClient;
import com.ntropy.common.client.VirtualUserQueryClient;
import com.ntropy.common.domain.UserScope;
import com.ntropy.common.dto.user.VirtualDatasetContext;
import com.ntropy.common.dto.user.VirtualUserDataset;
import com.ntropy.common.dto.user.VirtualUserIdentity;

import lombok.RequiredArgsConstructor;

/** user-service가 발행한 ActiveUserQueryClient/VirtualUserQueryClient를 account의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class UserActiveVirtualUsersAdapter implements UserPort {

    private final ActiveUserQueryClient activeUserQueryClient;
    private final VirtualUserQueryClient virtualUserQueryClient;

    @Override
    public List<Long> findActiveUserIds(UserScope scope) {
        return activeUserQueryClient.findActiveUserIds(scope);
    }

    @Override
    public SeededVirtualUserBatch findSeededVirtualUsers() {
        VirtualUserDataset dataset = virtualUserQueryClient.findSeededVirtualUsers();
        return new SeededVirtualUserBatch(toContext(dataset.context()), toUsers(dataset.users()));
    }

    private static VirtualDatasetExecutionContext toContext(VirtualDatasetContext context) {
        if (context == null) {
            return null;
        }
        return new VirtualDatasetExecutionContext(
                context.datasetVersion(), context.referenceDate(), context.randomSeed());
    }

    private static List<SeededVirtualUser> toUsers(List<VirtualUserIdentity> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .map(user -> new SeededVirtualUser(user.userId(), user.ordinal()))
                .toList();
    }
}
