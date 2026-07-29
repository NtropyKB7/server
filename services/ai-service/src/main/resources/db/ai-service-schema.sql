-- ==============================================================================
-- ai-service 전용 데이터베이스 DDL 스크립트
-- 모듈러 모놀리스 규칙: ai-service 소유의 AI_REPORT 테이블만 생성 및 관리
-- ==============================================================================

-- 1. AI_REPORT 테이블 생성 (AI 월간 리포트 결과 저장)
CREATE TABLE IF NOT EXISTS `AI_REPORT` (
                                           `report_id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'AI 리포트 고유 식별자 (PK)',
                                           `user_id`                BIGINT       NOT NULL COMMENT '회원 고유 식별자 (user-service 참조, FK 미설정)',
                                           `year_month`             VARCHAR(7)   NOT NULL COMMENT '리포트 대상 연월 (형식: YYYY-MM)',
    `financial_summary_json` JSON         NULL     COMMENT '재무 요약 및 진단 결과 스냅샷 (JSON 구조)',
    `recommendation_json`    JSON         NULL     COMMENT 'AI 금융상품 추천 정보 및 사유 (JSON 구조)',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '리포트 생성 일시',
    CONSTRAINT `PK_AI_REPORT` PRIMARY KEY (`report_id`)
    );

-- 2. 인덱스 생성 (조회 성능 최적화)
-- 모듈 간 경계 준수를 위해 user_id에 물리적 FK 제약조건은 걸지 않되,
-- (user_id, year_month) 조건의 초고속 조회를 위해 복합 인덱스(Index) 생성
CREATE INDEX `IDX_AI_REPORT_USER_YM` ON `AI_REPORT` (`user_id`, `year_month`);