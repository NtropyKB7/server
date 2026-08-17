package com.ntropy.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ntropy.user.client.LocalTokenVerifier;
import com.ntropy.user.security.JwtProvider;

/**
 * 로그인으로 발급된 access token이 Authorization 헤더를 통해 실제로 SecurityContext에 인증되는지 검증한다.
 * 토큰 발급(UserService.issueTokensForExistingUser)은 실제 OAuth 로그인과 가상회원 테스트 로그인이
 * 동일한 JwtProvider 경로를 쓰므로, 여기서 검증하는 "발급된 토큰이 인증된다"는 사실은 두 로그인 방식
 * 모두에 적용된다 — /api/auth/me 등 인증이 필요한 API가 실제로 동작하는지의 근거가 된다.
 */
class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider =
            new JwtProvider("testSecretKeytestSecretKeytestSecretKeytestSecretKey", 3_600_000L, 1_209_600_000L);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(new LocalTokenVerifier(jwtProvider));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesRequestUsingIssuedAccessToken() throws ServletException, IOException {
        String accessToken = jwtProvider.createAccessToken("7", "virtual-user-000007@ntropy.test", "ROLE_USER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(7L, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
        assertTrue(chain.invoked);
    }

    @Test
    void doesNotAuthenticateWithoutAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chain.invoked, "인증 실패해도 필터 체인은 계속 진행되어야 한다 (permitAll 경로 보호)");
    }

    @Test
    void doesNotAuthenticateWithTamperedToken() throws ServletException, IOException {
        String accessToken = jwtProvider.createAccessToken("7", "virtual-user-000007@ntropy.test", "ROLE_USER");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken + "tampered");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chain.invoked);
    }

    private static final class RecordingFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            invoked = true;
        }
    }
}
