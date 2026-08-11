package com.ntropy.notification.service;

import java.util.List;

import com.ntropy.common.client.ActiveUserQueryClient;

class StubActiveUserQueryClient implements ActiveUserQueryClient {

    private final List<Long> activeUserIds;

    StubActiveUserQueryClient(List<Long> activeUserIds) {
        this.activeUserIds = activeUserIds;
    }

    @Override
    public List<Long> findActiveUserIds() {
        return activeUserIds;
    }
}
