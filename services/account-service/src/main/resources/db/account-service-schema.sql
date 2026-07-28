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
