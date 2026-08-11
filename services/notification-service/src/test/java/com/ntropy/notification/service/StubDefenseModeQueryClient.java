package com.ntropy.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.common.client.DefenseModeQueryClient;
import com.ntropy.common.dto.defense.summary.DefenseCauseSummary;
import com.ntropy.common.dto.defense.summary.DefenseModeSummary;
import com.ntropy.common.exception.ServiceErrorCode;
import com.ntropy.common.exception.ServiceException;

/** getCurrent(userId)는 실제 구현처럼 활성 방어모드가 없으면 예외를 던진다. */
class StubDefenseModeQueryClient implements DefenseModeQueryClient {

    private static final ServiceErrorCode NOT_FOUND = new ServiceErrorCode() {
        @Override
        public int getStatusCode() {
            return 404;
        }

        @Override
        public String getMessage() {
            return "방어모드를 찾을 수 없습니다.";
        }
    };

    private final Map<Long, DefenseModeSummary> activeByUserId = new HashMap<>();

    StubDefenseModeQueryClient withActive(Long userId, DefenseModeSummary summary) {
        activeByUserId.put(userId, summary);
        return this;
    }

    @Override
    public List<DefenseCauseSummary> getCauses() {
        return List.of();
    }

    @Override
    public DefenseModeSummary getCurrent(Long userId) {
        DefenseModeSummary summary = activeByUserId.get(userId);
        if (summary == null) {
            throw new ServiceException(NOT_FOUND);
        }
        return summary;
    }
}
