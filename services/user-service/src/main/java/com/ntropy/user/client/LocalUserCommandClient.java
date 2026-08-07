package com.ntropy.user.client;

import com.ntropy.user.dto.OAuthLoginResponse;
import com.ntropy.common.client.UserCommandClient;
import com.ntropy.common.dto.user.OAuthLoginResult;
import com.ntropy.common.dto.user.TokenPair;
import com.ntropy.common.dto.user.command.UserUpdateCommand;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.exception.UserErrorCode;
import com.ntropy.user.domain.entity.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** user-service가 구현하는 인증·회원 명령 계약. */
@Component
@RequiredArgsConstructor
public class LocalUserCommandClient implements UserCommandClient {

    private final UserService userService;

    @Override
    public OAuthLoginResult loginWithOAuthCode(String provider, String code) {
        OAuthLoginResponse response = userService.processOAuthLoginWithCode(provider, code);
        return new OAuthLoginResult(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getUserId(),
                response.getName(),
                response.getEmail(),
                response.getOnboardingCompleted()
        );
    }

    @Override
    public TokenPair refreshAccessToken(String refreshToken) {
        TokenRefreshResponseDto response = userService.refreshAccessToken(refreshToken);
        return new TokenPair(response.getAccessToken(), response.getRefreshToken());
    }

    @Override
    public void logout(Long userId) {
        userService.logout(userId);
    }

    @Override
    public void updateUser(Long userId, UserUpdateCommand command) {
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_FOUND);
        }
        if (command.name() != null) {
            user.setName(command.name());
        }
        if (command.email() != null) {
            user.setEmail(command.email());
        }
        if (command.alarmAgree() != null) {
            user.setAlarmAgree(command.alarmAgree());
        }
        if (command.locationAgree() != null) {
            user.setLocationAgree(command.locationAgree());
        }
        userService.updateUser(user);
    }

    @Override
    public void deleteUser(Long userId) {
        userService.deleteUser(userId);
    }
}
