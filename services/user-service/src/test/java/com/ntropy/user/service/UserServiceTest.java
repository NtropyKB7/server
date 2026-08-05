package com.ntropy.user.service;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.auth.security.JwtProvider;
import com.ntropy.user.client.GoogleOAuthClient;
import com.ntropy.user.client.KakaoOAuthClient;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.model.AccessLog;
import com.ntropy.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks // 테스트 대상 객체
    private UserService userService;

    @Mock // 의존성 객체들을 Mock으로 주입
    private UserMapper userMapper;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private AccessLogService accessLogService;
    @Mock
    private HttpServletRequest request; // HttpServletRequest도 Mocking

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

        // HttpServletRequest Mocking 기본 설정
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Test-Agent");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
    }

    @Test
    @DisplayName("신규 회원가입 및 로그인 성공")
    void processOAuthLogin_new_user_success() {
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.empty());
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(1L); // insertUser 시 userId가 생성되었다고 가정
            return null; // void 메소드이므로 null 반환
        }).when(userMapper).insertUser(any(User.class));

        OAuthLoginResponse response = userService.processOAuthLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
        assertThat(response.getUserId()).isEqualTo(1L);
        verify(userMapper, times(1)).insertUser(any(User.class));
        verify(userMapper, never()).updateLoginInfo(any(User.class));
        verify(accessLogService, times(1)).saveAccessLog(any(AccessLog.class));
    }

    @Test
    @DisplayName("기존 회원 로그인 성공")
    void processOAuthLogin_existing_user_success() {
        testUser.setRefreshTokenHash("oldRefreshTokenHash");
        testUser.setRefreshTokenExpireAt(LocalDateTime.now().minusDays(1)); // 만료된 토큰
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn("newAccessToken");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("newRefreshToken");

        OAuthLoginResponse response = userService.processOAuthLogin(testUser);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
        assertThat(response.getUserId()).isEqualTo(1L);
        verify(userMapper, never()).insertUser(any(User.class));
        verify(userMapper, times(1)).updateLoginInfo(any(User.class)); // 기존 회원은 updateLoginInfo 호출
        verify(accessLogService, times(1)).saveAccessLog(any(AccessLog.class));
    }

    @Test
    @DisplayName("비활성 계정 로그인 시도 시 실패")
    void processOAuthLogin_inactive_user_failure() {
        testUser.setStatus("INACTIVE");
        when(userMapper.findByProviderAndProviderId(anyString(), anyString())).thenReturn(Optional.of(testUser));

        assertThrows(IllegalStateException.class, () -> userService.processOAuthLogin(testUser));
        verify(accessLogService, times(1)).saveAccessLog(any(AccessLog.class)); // 실패 로그 남김
    }

    @Test
    @DisplayName("Access Token 재발급 성공")
    void refreshAccessToken_success() {
        String oldRefreshToken = "validOldRefreshToken";
        String newAccessToken = "newAccessToken";
        String newRefreshToken = "newRefreshToken";

        testUser.setRefreshTokenHash(oldRefreshToken);
        when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);
        when(userMapper.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.of(testUser));
        when(jwtProvider.createAccessToken(anyString(), anyString(), anyString())).thenReturn(newAccessToken);
        when(jwtProvider.createRefreshToken(anyString())).thenReturn(newRefreshToken);

        TokenRefreshResponseDto response = userService.refreshAccessToken(oldRefreshToken);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(response.getRefreshToken()).isEqualTo(newRefreshToken);
        verify(userMapper, times(1)).updateLoginInfo(any(User.class)); // Refresh Token Rotation
        verify(accessLogService, times(1)).saveAccessLog(any(AccessLog.class));
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 시도 시 실패")
    void refreshAccessToken_invalid_token_failure() {
        String invalidRefreshToken = "invalidToken";
        when(jwtProvider.validateToken(invalidRefreshToken)).thenReturn(false);

        assertThrows(SecurityException.class, () -> userService.refreshAccessToken(invalidRefreshToken));
        verify(accessLogService, times(1)).saveAccessLog(any(AccessLog.class)); // 실패 로그 남김
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout_success() {
        userService.logout(testUser.getUserId());

        verify(userMapper, times(1)).invalidateRefreshToken(testUser.getUserId());
        verify(accessLogService, times(1)).saveAccessLog(any(AccessLog.class));
    }
}