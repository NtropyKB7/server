package com.ntropy.user.security;

import com.ntropy.auth.security.JwtProvider;
import com.ntropy.user.model.AccessLog;
//import com.ntropy.user.service.AccessLogService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// JWT 토큰 유효성 검증
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
//    private final AccessLogService accessLogService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String requestUri = request.getRequestURI();

        try {
            String token = parseBearerToken(request);

            if (token != null) {
                if (jwtProvider.validateToken(token)) {
                    String userId = jwtProvider.getUserId(token);
                    String role = jwtProvider.getRole(token);

                    List<GrantedAuthority> authorities = role != null ?
                            Stream.of(role).map(SimpleGrantedAuthority::new).collect(Collectors.toList()) :
                            Collections.emptyList();

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, authorities
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Security Context에 '{}' 인증 정보를 저장했습니다, uri: {}", authentication.getName(), requestUri);

//                    accessLogService.saveAccessLog(AccessLog.builder()
//                            .userId(Long.parseLong(userId))
//                            .ipAddress(ipAddress)
//                            .userAgent(userAgent)
//                            .requestUri(requestUri)
//                            .eventType("JWT_AUTHENTICATION_SUCCESS")
//                            .detail("JWT 토큰 유효성 검증 성공")
//                            .success(true)
//                            .build());

                } else {
//                    accessLogService.saveAccessLog(AccessLog.builder()
//                            .ipAddress(ipAddress)
//                            .userAgent(userAgent)
//                            .requestUri(requestUri)
//                            .eventType("JWT_AUTHENTICATION_FAILURE")
//                            .detail("유효하지 않은 JWT 토큰")
//                            .success(false)
//                            .build());
//                    log.warn("유효하지 않은 JWT 토큰이 감지되었습니다. URI: {}", requestUri);
                }
            }
        } catch (Exception e) {
            log.error("JWT 인증 설정에 실패했습니다: {}", e.getMessage());
//            accessLogService.saveAccessLog(AccessLog.builder()
//                    .ipAddress(ipAddress)
//                    .userAgent(userAgent)
//                    .requestUri(requestUri)
//                    .eventType("JWT_AUTHENTICATION_ERROR")
//                    .detail("JWT 인증 처리 중 예외 발생: " + e.getMessage())
//                    .success(false)
//                    .build());
        }

        filterChain.doFilter(request, response);
    }

    private String parseBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}