-- ============================================================
-- SETTLEMENT에 UNMATCHED 집계 지원 컬럼 추가
-- - user_id: job_id가 NULL인 UNMATCHED 행은 JOB 조인으로 사용자를 알 수 없어 직접 저장
-- - status: MATCHED/UNMATCHED 구분 (기존 행은 전부 MATCHED로 백필)
-- - transaction_count: UNMATCHED는 하루치 미매칭 거래를 합산해 저장하므로 몇 건이
--   합쳐졌는지 별도로 알아야 한다 (MATCHED는 거래 1건=행 1개라 항상 1).
-- - job_id/account_transaction_id: UNMATCHED 행은 특정 잡/거래를 가리킬 수 없어 nullable로 변경
-- 이미 배포된 스키마에 대한 증분 추가.
-- ============================================================

ALTER TABLE `SETTLEMENT`
    ADD COLUMN `user_id` BIGINT NULL COMMENT '사용자 ID (job_id가 NULL인 UNMATCHED 행 식별용)' AFTER `settlement_id`;

UPDATE `SETTLEMENT` s
JOIN `JOB` j ON s.job_id = j.job_id
SET s.user_id = j.user_id;

ALTER TABLE `SETTLEMENT`
    MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `SETTLEMENT`
    ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'MATCHED' COMMENT 'MATCHED/UNMATCHED' AFTER `user_id`;

ALTER TABLE `SETTLEMENT`
    ADD COLUMN `transaction_count` INT NOT NULL DEFAULT 1 COMMENT '이 행에 합산된 거래 건수 (MATCHED는 항상 1)' AFTER `actual_amount`;

ALTER TABLE `SETTLEMENT`
    MODIFY COLUMN `job_id` BIGINT NULL;

ALTER TABLE `SETTLEMENT`
    MODIFY COLUMN `account_transaction_id` BIGINT NULL;

CREATE INDEX `IDX_SETTLEMENT_USER_ID` ON `SETTLEMENT` (`user_id`);
