-- FIN-005: 기업은행 상대계좌 예금주 원본과 소득-일자리 논리 참조를 추가한다.
-- 서비스 간 DB 외래키는 생성하지 않는다.

ALTER TABLE ACCOUNT_TRANSACTION
    ADD COLUMN job_id BIGINT NULL
        COMMENT 'work-service JOB.job_id 논리 참조 (크로스 도메인 FK 없음)'
        AFTER account_id,
    ADD COLUMN desc1 VARCHAR(255) NULL
        COMMENT 'resAccountDesc1, 은행별 의미가 달라 원본 필드명 유지'
        AFTER after_balance,
    ADD INDEX ix_account_transaction_job (job_id);
