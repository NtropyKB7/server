-- ============================================================
-- account-service DDL
-- 현재는 이슈 #5(CODEF 초기 세팅/PoC) 범위의 CODEF_CONNECTION만 포함.
-- 계좌/거래 데이터 테이블은 이슈 #4(기능 구현)에서 CODEF 실제 응답 구조 확인 후 추가 예정.
-- ============================================================

CREATE TABLE IF NOT EXISTS CODEF_CONNECTION
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL COMMENT 'user-service USER.id 참조 (크로스 도메인 FK 없음)',
    connected_id VARCHAR(100) NOT NULL COMMENT 'CODEF 커넥티드 아이디 (계정 등록 API 응답값)',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_codef_connection_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- CODEF OAuth2 accessToken 캐시. client_credentials 방식이라 사용자 단위가 아닌 클라이언트(서비스) 단위로 존재.
-- 지금은 DB로만 캐싱하고, 추후 Redis 도입 시 CodefTokenStore의 Redis 구현체로 대체 예정 (DEVLOG 참고).
CREATE TABLE IF NOT EXISTS CODEF_TOKEN
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_type VARCHAR(20)   NOT NULL COMMENT 'SANDBOX, DEMO, API',
    client_id    VARCHAR(100)  NOT NULL COMMENT '토큰을 발급받은 CODEF OAuth 클라이언트 식별자',
    access_token VARCHAR(2000) NOT NULL,
    expires_at   DATETIME      NOT NULL COMMENT 'accessToken 만료 시각 (발급 시각 + expires_in)',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_codef_token_lookup (service_type, client_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
