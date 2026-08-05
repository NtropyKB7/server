package com.ntropy.user.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLog {

    private Long logId;             // 로그 ID
    private Long userId;            // 사용자 ID (로그인 전이면 null)
    private String email;           // 이메일 (로그인 전이면 null)
    private String ipAddress;       // 접근 IP 주소
    private String userAgent;       // User-Agent 정보
    private String requestUri;      // 요청 URI
    private String eventType;       // 이벤트 타입 (LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_REFRESH, UNAUTHORIZED_ACCESS 등)
    private String detail;          // 상세 내용 (예: 실패 사유)
    private LocalDateTime createdAt; // 생성일시
    private Boolean success;        // 성공 여부
}