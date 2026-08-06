package com.ntropy.bff.controller.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.bff.dto.user.request.TokenRefreshRequest;
import com.ntropy.bff.dto.user.response.OAuthLoginResponse;
import com.ntropy.bff.dto.user.response.TokenRefreshResponse;
import com.ntropy.bff.dto.user.response.UserResponse;
import com.ntropy.common.client.UserCommandClient;
import com.ntropy.common.client.UserQueryClient;
import com.ntropy.common.dto.user.UserSummary;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.bff.dto.common.ErrorCode;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

@Api(tags = "인증")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserCommandClient userCommandClient;
    private final UserQueryClient userQueryClient;

    @ApiOperation("소셜 로그인")
    @GetMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> oauthLogin(
            @PathVariable String provider,
            @RequestParam("code") String code
    ) {
        return ApiResponse.success(
                OAuthLoginResponse.from(userCommandClient.loginWithOAuthCode(provider, code))
        );
    }

    @ApiOperation("내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(
            @RequestParam Long userId // TODO: AUTH 연동 후 인증 사용자 ID 사용
    ) {
        UserSummary summary = userQueryClient.getUserSummary(userId);
        if (summary == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND);
        }
        return ApiResponse.success(UserResponse.from(summary));
    }

    @ApiOperation("로그아웃")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestParam Long userId // TODO: AUTH 연동 후 인증 사용자 ID 사용
    ) {
        userCommandClient.logout(userId);
        return ApiResponse.success(200, "로그아웃되었습니다.", null);
    }

    @ApiOperation("Access Token 재발급")
    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refresh(@RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(
                TokenRefreshResponse.from(userCommandClient.refreshAccessToken(request.getRefreshToken()))
        );
    }
}
