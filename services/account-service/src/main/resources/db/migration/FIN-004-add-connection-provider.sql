-- FIN-004: 기존 CODEF_CONNECTION을 CODEF/NTROPY 제공자별로 보유할 수 있도록 변경한다.
-- 이 스크립트는 FIN-004 적용 전 스키마에 한 번만 실행한다.

ALTER TABLE CODEF_CONNECTION
    ADD COLUMN provider VARCHAR(10) NOT NULL DEFAULT 'CODEF'
        COMMENT '연결 제공자: CODEF(실제 CODEF 연동), NTROPY(가상 연결)'
        AFTER user_id;

ALTER TABLE CODEF_CONNECTION
    DROP INDEX uk_codef_connection_user,
    ADD UNIQUE KEY uk_codef_connection_user_provider (user_id, provider);

-- DEFAULT 'CODEF'로 백필된 기존 연결 데이터 확인용
SELECT id, user_id, provider, connected_id, registered_institution_keys
FROM CODEF_CONNECTION
WHERE provider = 'CODEF';
