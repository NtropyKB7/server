package com.ntropy.notification.service;

import java.util.HashMap;
import java.util.Map;

import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.dto.user.UserSummary;

/** 알림 수신 동의(alarm_agree) 값을 회원별로 지정할 수 있는 테스트용 스텁. */
class StubUserQueryClient implements UserQueryClient {

    private final Map<Long, Boolean> alarmAgreeByUserId = new HashMap<>();

    StubUserQueryClient withAlarmAgree(Long userId, boolean alarmAgree) {
        alarmAgreeByUserId.put(userId, alarmAgree);
        return this;
    }

    @Override
    public UserSummary getUserSummary(Long userId) {
        if (!alarmAgreeByUserId.containsKey(userId)) {
            return null;
        }
        return new UserSummary(userId, "테스트유저", "test@ntropy.com", "KAKAO",
                alarmAgreeByUserId.get(userId), true, true);
    }
}
