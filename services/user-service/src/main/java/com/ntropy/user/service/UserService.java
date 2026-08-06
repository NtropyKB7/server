package com.ntropy.user.service;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.auth.security.JwtProvider;
import com.ntropy.user.client.GoogleOAuthClient;
import com.ntropy.user.client.KakaoOAuthClient;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;
    private final JwtProvider jwtProvider;

    @Transactional
    public OAuthLoginResponse processOAuthLoginWithCode(String provider, String code, HttpServletRequest request) {
        log.info("소셜 로그인 처리 시작: provider={}", provider);
        User oauthUserParam = getOAuthUser(provider, code);
        return processOAuthLogin(oauthUserParam, request);
    }

    private User getOAuthUser(String provider, String code) {
        if ("kakao".equalsIgnoreCase(provider)) {
            String accessToken = kakaoOAuthClient.getAccessToken(code);
            return kakaoOAuthClient.getKakaoUser(accessToken);
        } else if ("google".equalsIgnoreCase(provider)) {
            String accessToken = googleOAuthClient.getAccessToken(code);
            return googleOAuthClient.getGoogleUser(accessToken);
        } else {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자입니다: " + provider);
        }
    }

    @Transactional
    public OAuthLoginResponse processOAuthLogin(User oauthUserParam, HttpServletRequest request) {
        Optional<User> optionalUser = userMapper.findByProviderAndProviderId(
                oauthUserParam.getProvider(),
                oauthUserParam.getProviderId()
        );

        User user;
        String accessToken;
        String refreshToken;

        if (optionalUser.isPresent()) {
            user = optionalUser.get();
            log.info("기존 회원 로그인 처리: userId={}", user.getUserId());

            if (!"ACTIVE".equals(user.getStatus())) {
                throw new IllegalStateException("로그인할 수 없는 회원 상태입니다: " + user.getStatus());
            }

            accessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
            refreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

            user.setRefreshTokenHash(refreshToken);
            user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
            userMapper.updateLoginInfo(user);

        } else {
            user = oauthUserParam;
            log.info("신규 회원가입 처리: provider={}, email={}", user.getProvider(), user.getEmail());
            user.setStatus("ACTIVE");
            user.setRole("ROLE_USER");
            user.setTermsAgreed(true);

            accessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
            refreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

            user.setRefreshTokenHash(refreshToken);
            user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
            userMapper.insertUser(user);
        }

        return OAuthLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .onboardingCompleted(user.getOnboardingCompleted())
                .build();
    }

    public User getUserById(Long userId) {
        return userMapper.findById(userId);
    }

    @Transactional
    public void updateUser(User user) {
        userMapper.updateUser(user);
    }

    @Transactional
    public void deleteUser(Long userId, HttpServletRequest request) {
        userMapper.deleteUser(userId);
    }

    @Transactional
    public void logout(Long userId, HttpServletRequest request) {
        userMapper.invalidateRefreshToken(userId);
        log.info("사용자 로그아웃: userId={}의 Refresh Token을 폐기했습니다.", userId);
    }

    @Transactional
    public TokenRefreshResponseDto refreshAccessToken(String refreshToken, HttpServletRequest request) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new SecurityException("유효하지 않은 Refresh Token입니다.");
        }

        User user = userMapper.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new SecurityException("Refresh Token에 해당하는 사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

        user.setRefreshTokenHash(newRefreshToken);
        user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
        userMapper.updateLoginInfo(user);

        log.info("Access Token 재발급 및 Refresh Token 회전 완료: userId={}", user.getUserId());

        return new TokenRefreshResponseDto(newAccessToken, newRefreshToken);
    }
}