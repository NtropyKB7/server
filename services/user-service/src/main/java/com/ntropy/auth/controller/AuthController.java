package com.ntropy.auth.controller;

import com.ntropy.auth.dto.OAuthLoginRequest;
import com.ntropy.user.model.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    /*
     OAuth 소셜 로그인 및 회원가입
     */
    @PostMapping("/oauth/{provider}")
    public ResponseEntity<User> oauthLogin(
            @PathVariable String provider,
            @RequestBody OAuthLoginRequest requestDto) {

        log.info("==========> 소셜 로그인 요청 도착! Provider: {}, Code: {}",
                provider, requestDto.getCode());

        // Service를 호출해서 [카카오 통신 -> DB 신규가입/로그인 분기 처리] 진행
        User loginUser = userService.processOAuthLoginWithCode(provider, requestDto.getCode());

        return ResponseEntity.ok(loginUser);
    }
}