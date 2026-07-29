package com.ntropy.user.service;

import com.ntropy.user.model.User;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.client.KakaoOAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final KakaoOAuthClient kakaoOAuthClient;

     // 1. 소셜 로그인 메인 메서드
    @Transactional
    public User processOAuthLoginWithCode(String provider, String code) {
        log.info("OAuth 로그인 진행 - provider: {}, code: {}", provider, code);

        User oauthUserParam;

        if ("kakao".equalsIgnoreCase(provider)) {
            // 1. code -> Access Token 받기
            String accessToken = kakaoOAuthClient.getAccessToken(code);
            // 2. Access Token -> 유저 프로필 조회하기
            oauthUserParam = kakaoOAuthClient.getKakaoUser(accessToken);
        } else {
            throw new IllegalArgumentException("현재 지원하지 않는 소셜 로그인 제공자입니다: " + provider);
        }

        // 3. 조회한 진짜 프로필로 DB 분기 처리 (기존 로직 그대로 활용)
        return processOAuthLogin(oauthUserParam);
    }

     // 2. 회원가입, 로그인 분기 처리 메서드
    @Transactional
    public User processOAuthLogin(User oauthUserParam) {
        Optional<User> optionalUser = userMapper.findByProviderAndProviderId(
                oauthUserParam.getProvider(),
                oauthUserParam.getProviderId()
        );

        if (optionalUser.isPresent()) {
            User existingUser = optionalUser.get();
            log.info("기존 회원 로그인: userId = {}", existingUser.getUserId());

            userMapper.updateLoginInfo(existingUser);
            return existingUser;
        } else {
            log.info("신규 회원가입 진행: provider = {}, email = {}",
                    oauthUserParam.getProvider(), oauthUserParam.getEmail());

            oauthUserParam.setStatus("ACTIVE");
            oauthUserParam.setRole("ROLE_USER");
            userMapper.insertUser(oauthUserParam);

            return oauthUserParam;
        }
    }

    // 3. 회원 프로필 조회
    public User getUserById(Long userId) {
        return userMapper.findById(userId);
    }

    // 4. 회원 정보 수정
    @Transactional
    public void updateUser(User user) {
        userMapper.updateUser(user);
    }

    // 5. 회원 탈퇴
    @Transactional
    public void deleteUser(Long userId) {
        userMapper.deleteUser(userId);
    }
}