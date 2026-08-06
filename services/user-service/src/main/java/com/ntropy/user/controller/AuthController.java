package com.ntropy.user.controller;

import com.ntropy.auth.dto.OAuthLoginResponse;
import com.ntropy.user.dto.TokenRefreshRequestDto;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.model.User;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

// 소셜 로그인 및 인증 관련 API를 처리
@RestController("userAuthController")
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
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = Long.valueOf(userDetails.getUsername());
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        String userId = userDetails.getUsername();
        log.info("==========> 로그아웃 요청: userId={}", userId);
        userService.logout(Long.parseLong(userId), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponseDto> refresh(@RequestBody TokenRefreshRequestDto requestDto, HttpServletRequest request) {
        log.info("==========> Access Token 재발급 요청");
        TokenRefreshResponseDto responseDto = userService.refreshAccessToken(requestDto.getRefreshToken(), request);
        return ResponseEntity.ok(responseDto);
    }
}