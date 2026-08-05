package com.ntropy.user.service;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.auth.security.JwtProvider;
import com.ntropy.user.client.GoogleOAuthClient;
import com.ntropy.user.client.KakaoOAuthClient;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.model.AccessLog;
import com.ntropy.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;
    private final JwtProvider jwtProvider;
//    private final AccessLogService accessLogService;
    private final HttpServletRequest request;

    @Transactional
    public OAuthLoginResponse processOAuthLoginWithCode(String provider, String code) {
        log.info("소셜 로그인 처리 시작: provider={}", provider);

        User oauthUserParam = getOAuthUser(provider, code);
        return processOAuthLogin(oauthUserParam);
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
    public OAuthLoginResponse processOAuthLogin(User oauthUserParam) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String requestUri = request.getRequestURI();

        try {
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
//                    accessLogService.saveAccessLog(AccessLog.builder()
//                            .userId(user.getUserId())
//                            .email(user.getEmail())
//                            .ipAddress(ipAddress)
//                            .userAgent(userAgent)
//                            .requestUri(requestUri)
//                            .eventType("LOGIN_FAILURE")
//                            .detail("비활성 계정 로그인 시도: " + user.getStatus())
//                            .success(false)
//                            .build());
                    throw new IllegalStateException("로그인할 수 없는 회원 상태입니다: " + user.getStatus());
                }

                accessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
                refreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

                user.setRefreshTokenHash(refreshToken);
                user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
                userMapper.updateLoginInfo(user);

//                accessLogService.saveAccessLog(AccessLog.builder()
//                        .userId(user.getUserId())
//                        .email(user.getEmail())
//                        .ipAddress(ipAddress)
//                        .userAgent(userAgent)
//                        .requestUri(requestUri)
//                        .eventType("LOGIN_SUCCESS")
//                        .detail("기존 회원 로그인 성공")
//                        .success(true)
//                        .build());

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

//                accessLogService.saveAccessLog(AccessLog.builder()
//                        .userId(user.getUserId())
//                        .email(user.getEmail())
//                        .ipAddress(ipAddress)
//                        .userAgent(userAgent)
//                        .requestUri(requestUri)
//                        .eventType("SIGNUP_SUCCESS")
//                        .detail("신규 회원가입 및 로그인 성공")
//                        .success(true)
//                        .build());
            }

            return OAuthLoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .userId(user.getUserId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .onboardingCompleted(user.getOnboardingCompleted())
                    .build();

        } catch (Exception e) {
            // 중복 로그 방지 로직 추가
            if (!(e instanceof IllegalStateException && e.getMessage().contains("로그인할 수 없는 회원 상태입니다"))) {
//                accessLogService.saveAccessLog(AccessLog.builder()
//                        .email(oauthUserParam.getEmail())
//                        .ipAddress(ipAddress)
//                        .userAgent(userAgent)
//                        .requestUri(requestUri)
//                        .eventType("LOGIN_FAILURE")
//                        .detail("로그인/회원가입 처리 중 오류: " + e.getMessage())
//                        .success(false)
//                        .build());
            }
            throw e;
        }
    }


    public User getUserById(Long userId) {
        return userMapper.findById(userId);
    }

    @Transactional
    public void updateUser(User user) {
        userMapper.updateUser(user);
    }


    @Transactional
    public void deleteUser(Long userId) {
        userMapper.deleteUser(userId);
//        accessLogService.saveAccessLog(AccessLog.builder()
//                .userId(userId)
//                .ipAddress(request.getRemoteAddr())
//                .userAgent(request.getHeader("User-Agent"))
//                .requestUri(request.getRequestURI())
//                .eventType("USER_DEACTIVATION")
//                .detail("회원 탈퇴 처리")
//                .success(true)
//                .build());
    }

    @Transactional
    public void logout(Long userId) {
        userMapper.invalidateRefreshToken(userId);
        log.info("사용자 로그아웃: userId={}의 Refresh Token을 폐기했습니다.", userId);
//        accessLogService.saveAccessLog(AccessLog.builder()
//                .userId(userId)
//                .ipAddress(request.getRemoteAddr())
//                .userAgent(request.getHeader("User-Agent"))
//                .requestUri(request.getRequestURI())
//                .eventType("LOGOUT_SUCCESS")
//                .detail("로그아웃 성공")
//                .success(true)
//                .build());
    }

    @Transactional
    public TokenRefreshResponseDto refreshAccessToken(String refreshToken) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String requestUri = request.getRequestURI();

        try {
            if (!jwtProvider.validateToken(refreshToken)) {
//                accessLogService.saveAccessLog(AccessLog.builder()
//                        .ipAddress(ipAddress)
//                        .userAgent(userAgent)
//                        .requestUri(requestUri)
//                        .eventType("TOKEN_REFRESH_FAILURE")
//                        .detail("유효하지 않은 Refresh Token")
//                        .success(false)
//                        .build());
                throw new SecurityException("유효하지 않은 Refresh Token입니다.");
            }

            User user = userMapper.findByRefreshToken(refreshToken)
                    .orElseThrow(() -> {
//                        accessLogService.saveAccessLog(AccessLog.builder()
//                                .ipAddress(ipAddress)
//                                .userAgent(userAgent)
//                                .requestUri(requestUri)
//                                .eventType("TOKEN_REFRESH_FAILURE")
//                                .detail("DB에서 Refresh Token에 해당하는 사용자 없음")
//                                .success(false)
//                                .build());
                        return new SecurityException("Refresh Token에 해당하는 사용자를 찾을 수 없습니다.");
                    });

            String newAccessToken = jwtProvider.createAccessToken(String.valueOf(user.getUserId()), user.getEmail(), user.getRole());
            String newRefreshToken = jwtProvider.createRefreshToken(String.valueOf(user.getUserId()));

            user.setRefreshTokenHash(newRefreshToken);
            user.setRefreshTokenExpireAt(LocalDateTime.now().plusWeeks(2));
            userMapper.updateLoginInfo(user);

            log.info("Access Token 재발급 및 Refresh Token 회전 완료: userId={}", user.getUserId());
//            accessLogService.saveAccessLog(AccessLog.builder()
//                    .userId(user.getUserId())
//                    .email(user.getEmail())
//                    .ipAddress(ipAddress)
//                    .userAgent(userAgent)
//                    .requestUri(requestUri)
//                    .eventType("TOKEN_REFRESH_SUCCESS")
//                    .detail("Access Token 재발급 성공")
//                    .success(true)
//                    .build());

            return new TokenRefreshResponseDto(newAccessToken, newRefreshToken);
        } catch (Exception e) {
            if (!(e instanceof SecurityException && e.getMessage().contains("유효하지 않은 Refresh Token입니다."))) {
//                accessLogService.saveAccessLog(AccessLog.builder()
//                        .ipAddress(ipAddress)
//                        .userAgent(userAgent)
//                        .requestUri(requestUri)
//                        .eventType("TOKEN_REFRESH_FAILURE")
//                        .detail("Access Token 재발급 중 오류: " + e.getMessage())
//                        .success(false)
//                        .build());
            }
            throw e;
        }
    }
}