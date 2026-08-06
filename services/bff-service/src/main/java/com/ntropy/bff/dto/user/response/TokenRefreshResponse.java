package com.ntropy.bff.dto.user.response;

import com.ntropy.common.dto.user.TokenPair;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken
) {

    public static TokenRefreshResponse from(TokenPair tokenPair) {
        return new TokenRefreshResponse(tokenPair.accessToken(), tokenPair.refreshToken());
    }
}
