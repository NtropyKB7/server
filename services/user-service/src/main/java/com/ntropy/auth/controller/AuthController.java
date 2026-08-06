package com.ntropy.auth.controller;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.user.model.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

// 소셜 로그인 및 인증 관련 API를 처리
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    // OAuth 2.0 소셜 로그인 처리
    @GetMapping("/oauth/{provider}")
    public ResponseEntity<OAuthLoginResponse> oauthLogin(
            @PathVariable String provider,
            @RequestParam("code") String code,
            HttpServletRequest request) {

        log.info("소셜 로그인 요청 수신: provider={}, code={}", provider, code);

        OAuthLoginResponse responseDto = userService.processOAuthLoginWithCode(provider, code, request);

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/me")
    public ResponseEntity<User> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.valueOf(userDetails.getUsername());
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
}