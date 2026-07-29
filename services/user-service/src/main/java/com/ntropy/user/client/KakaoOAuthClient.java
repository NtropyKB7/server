package com.ntropy.user.client;

import com.ntropy.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String CLIENT_ID = "3888ed88cfd394028fc558314e031d66";
    private final String REDIRECT_URI = "http://localhost:8080/api/auth/oauth/kakao";

    // 인가 코드(code)로 카카오 Access Token 발급
    public String getAccessToken(String code) {
        String tokenUrl = "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", CLIENT_ID);
        params.add("redirect_uri", REDIRECT_URI);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("==========> 카카오 Access Token 발급 실패: {}", e.getMessage());
            throw new RuntimeException("카카오 Access Token 발급 중 오류 발생", e);
        }
    }

    // Access Token으로 카카오 사용자 프로필 정보 가져오기
    public User getKakaoUser(String accessToken) {
        String userInfoUrl = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            // 1. 카카오 회원 고유 ID (필수값)
            String providerId = jsonNode.get("id").asText();

            // 2. 닉네임과 이메일
            JsonNode kakaoAccount = jsonNode.path("kakao_account");
            String name = kakaoAccount.path("profile").path("nickname").asText("카카오유저");
            String email = kakaoAccount.path("email").asText(providerId + "@kakao.com"); // 이메일 미동의 시 기본값 지정

            log.info("==========> 카카오 프로필 조회 성공! ID: {}, Name: {}, Email: {}", providerId, name, email);

            // User 모델에 담아서 리턴
            User user = new User();
            user.setProvider("KAKAO");
            user.setProviderId(providerId);
            user.setEmail(email);
            user.setName(name);
            return user;

        } catch (Exception e) {
            log.error("==========> 카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new RuntimeException("카카오 사용자 정보 조회 중 오류 발생", e);
        }
    }
}