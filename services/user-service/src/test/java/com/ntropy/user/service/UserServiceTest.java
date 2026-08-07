package com.ntropy.user.service;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.auth.security.JwtProvider;
import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.client.GoogleOAuthClient;
import com.ntropy.user.client.KakaoOAuthClient;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserMapper userMapper;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private JwtProvider jwtProvider;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("test@example.com")
                .name("Test User")
                .provider("KAKAO")
                .providerId("kakao123")
                .status("ACTIVE")
                .role("ROLE_USER")
                .onboardingCompleted(false)
                .termsAgreed(true)
                .build();
    }

    @Test
    @DisplayName("신규 회원가입 및 로그인 성공")
    void processOAuthLogin_new_user_success() {
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.empty());
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(1L);
            return null;
        }).when(userMapper).insertUser(any(User.class));

        OAuthLoginResponse response = userService.processOAuthLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getUserId()).isEqualTo(1L);
        verify(userMapper, times(1)).insertUser(any(User.class));
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
        verify(jwtProvider).createAccessToken("1", testUser.getEmail(), testUser.getRole());
    }

    @Test
    @DisplayName("기존 회원 로그인 성공")
    void processOAuthLogin_existing_user_success() {
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        OAuthLoginResponse response = userService.processOAuthLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
    }

    @Test
    @DisplayName("비활성 계정 로그인 시도 시 실패")
    void processOAuthLogin_inactive_user_failure() {
        testUser.setStatus("INACTIVE");
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.processOAuthLogin(testUser));
        assertThat(exception.getStatusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Access Token 재발급 성공")
    void refreshAccessToken_success() {
        String oldRefreshToken = "validOldRefreshToken";
        when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);
        when(userMapper.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        TokenRefreshResponseDto response = userService.refreshAccessToken(oldRefreshToken);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 시도 시 실패")
    void refreshAccessToken_invalid_token_failure() {
        String invalidRefreshToken = "invalidToken";
        when(jwtProvider.validateToken(invalidRefreshToken)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.refreshAccessToken(invalidRefreshToken));
        assertThat(exception.getStatusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("지원하지 않는 소셜 제공자로 로그인 시도 시 실패")
    void processOAuthLoginWithCode_unsupported_provider_failure() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> userService.processOAuthLoginWithCode("naver", "code"));
        assertThat(exception.getStatusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() {
        userService.logout(testUser.getUserId());

        verify(userMapper, times(1)).invalidateRefreshToken(testUser.getUserId());
    }
}
