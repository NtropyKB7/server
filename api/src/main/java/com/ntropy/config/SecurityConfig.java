package com.ntropy.config;

import com.ntropy.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 배포 단위 전체의 인증 정책.
 * 경로별 허용 규칙이 전 모듈 엔드포인트에 걸리므로 도메인 모듈이 아니라 api 모듈에 둔다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight는 Authorization 헤더가 없으므로 필터 단계에서 막히면 안 된다
                        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 로그인/토큰 재발급은 토큰 없이 호출된다 (/me, /logout은 인증 필요)
                        .antMatchers("/api/auth/oauth/**", "/api/auth/refresh", "/oauth2/**").permitAll()
                        // 가상회원 테스트 로그인 (이슈 #166). virtual-test.enabled=false면 컨트롤러가 404를 반환한다
                        .antMatchers(HttpMethod.POST, "/api/auth/virtual-login").permitAll()
                        // PortOne이 외부에서 호출한다. PortOneWebhookVerifier의 서명 검증으로 보호된다
                        .antMatchers(HttpMethod.POST, "/api/subscriptions/webhook").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/subscriptions/plans").permitAll()
                        .antMatchers("/swagger-ui.html", "/webjars/**", "/v2/api-docs",
                                "/swagger-resources/**", "/resources/**").permitAll()
                        // 헬스체크는 인증 없이 접근한다
                        .antMatchers("/health").permitAll()
                        // [테스트용] HolidayTestController - 검증 끝나면 이 줄과 컨트롤러 같이 지울 것
                        .antMatchers("/internal/test/**").permitAll()
                        .anyRequest().authenticated()
                )
                // httpBasic/formLogin을 모두 끄면 기본 EntryPoint가 403을 반환하므로 401로 교정한다
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "인증 정보가 없습니다."))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다."))
                );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 인증 실패 응답을 bff의 ApiResponse 오류 형식과 맞춘다. */
    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"status\":%d,\"message\":\"%s\",\"data\":null}", status, message)
        );
    }

    /** ServletConfig.addCorsMappings는 DispatcherServlet 안쪽이라 보안 필터 단계에는 적용되지 않는다. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "https://kbntropy.vercel.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
