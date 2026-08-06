package com.ntropy.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.user.model.AccessLog;
import com.ntropy.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "AUTH_LOG")
public class AccessLogService {

    private final ObjectMapper objectMapper;

    // 가장 기본적인 로그 저장 메소드
    private void log(AccessLog accessLog) {
        try {
            String logJson = objectMapper.writeValueAsString(accessLog);
            log.info(logJson);
        } catch (JsonProcessingException e) {
            log.error("AccessLog JSON 변환 실패", e);
        }
    }

    // 로그인/회원가입 성공 로그
    public void logLoginSuccess(HttpServletRequest request, User user, String eventType, String detail) {
        AccessLog accessLog = AccessLog.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .requestUri(request.getRequestURI())
                .eventType(eventType)
                .detail(detail)
                .success(true)
                .build();
        log(accessLog);
    }

    // 로그인/회원가입 실패 로그
    public void logLoginFailure(HttpServletRequest request, String email, String eventType, String detail) {
        AccessLog accessLog = AccessLog.builder()
                .email(email)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .requestUri(request.getRequestURI())
                .eventType(eventType)
                .detail(detail)
                .success(false)
                .build();
        log(accessLog);
    }

    // 인증된 사용자의 활동 로그 (로그아웃, 토큰 재발급 등)
    public void logActivity(HttpServletRequest request, Long userId, String eventType, String detail, boolean success) {
        AccessLog accessLog = AccessLog.builder()
                .userId(userId)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .requestUri(request.getRequestURI())
                .eventType(eventType)
                .detail(detail)
                .success(success)
                .build();
        log(accessLog);
    }
}