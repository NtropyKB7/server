package com.ntropy.bff.dto.user.response;

import com.ntropy.common.dto.user.UserSummary;

public record UserResponse(
        Long userId,
        String name,
        String email,
        String provider,
        Boolean alarmAgree,
        Boolean locationAgree,
        Boolean onboardingCompleted
) {

    public static UserResponse from(UserSummary summary) {
        return new UserResponse(
                summary.userId(),
                summary.name(),
                summary.email(),
                summary.provider(),
                summary.alarmAgree(),
                summary.locationAgree(),
                summary.onboardingCompleted()
        );
    }
}
