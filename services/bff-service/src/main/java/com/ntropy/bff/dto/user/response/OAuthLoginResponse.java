package com.ntropy.bff.dto.user.response;

import com.ntropy.common.dto.user.OAuthLoginResult;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String name,
        String email,
        Boolean onboardingCompleted
) {

    public static OAuthLoginResponse from(OAuthLoginResult result) {
        return new OAuthLoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.userId(),
                result.name(),
                result.email(),
                result.onboardingCompleted()
        );
    }
}
