package com.ntropy.user.controller;

import com.ntropy.user.dto.TokenRefreshRequestDto;
import com.ntropy.user.dto.TokenRefreshResponseDto;
import com.ntropy.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController("userAuthController")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String userId, HttpServletRequest request) {
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