package com.ntropy.user.service;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.auth.security.JwtProvider;
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
import org.springframework.mock.web.MockHttpServletRequest;

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

    private MockHttpServletRequest request;
    private User testUser;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();

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

        OAuthLoginResponse response = userService.processOAuthLogin(testUser, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).insertUser(any(User.class));
    }

    @Test
    @DisplayName("기존 회원 로그인 성공")
    void processOAuthLogin_existing_user_success() {
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        OAuthLoginResponse response = userService.processOAuthLogin(testUser, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
    }

    @Test
    @DisplayName("비활성 계정 로그인 시도 시 실패")
    void processOAuthLogin_inactive_user_failure() {
        testUser.setStatus("INACTIVE");
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));

        assertThrows(IllegalStateException.class, () -> userService.processOAuthLogin(testUser, request));
    }

    @Test
    @DisplayName("Access Token 재발급 성공")
    void refreshAccessToken_success() {
        String oldRefreshToken = "validOldRefreshToken";
        when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);
        when(userMapper.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        TokenRefreshResponseDto response = userService.refreshAccessToken(oldRefreshToken, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        verify(userMapper, times(1)).updateLoginInfo(any(User.class));
        verify(accessLogService, times(1)).logActivity(any(), any(), anyString(), anyString(), eq(true));
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 시도 시 실패")
    void refreshAccessToken_invalid_token_failure() {
        String invalidRefreshToken = "invalidToken";
        when(jwtProvider.validateToken(invalidRefreshToken)).thenReturn(false);

        assertThrows(SecurityException.class, () -> userService.refreshAccessToken(invalidRefreshToken, request));
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() {
        userService.logout(testUser.getUserId(), request);

        verify(userMapper, times(1)).invalidateRefreshToken(testUser.getUserId());
    }
}