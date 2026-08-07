package com.ntropy.common.client;

import com.ntropy.common.dto.user.OAuthLoginResult;
import com.ntropy.common.dto.user.TokenPair;
import com.ntropy.common.dto.user.command.UserUpdateCommand;

/** 회원 도메인이 제공할 인증·회원 명령 계약. */
public interface UserCommandClient {

    OAuthLoginResult loginWithOAuthCode(String provider, String code);

    TokenPair refreshAccessToken(String refreshToken);

    void logout(Long userId);

    void updateUser(Long userId, UserUpdateCommand command);

    void deleteUser(Long userId);
}
