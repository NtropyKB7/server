-- ============================================================
-- SETTLEMENT 테이블 추가 (입금 거래-플랫폼 매칭 및 정산 처리)
-- 이미 배포된 스키마에 대한 증분 추가라 work-service-schema.sql을 직접 고치지 않고
-- 별도 파일로 둔다 (account-service의 issue-63-add-account-status.sql과 동일 방식).
-- account_transaction_id는 account-service ACCOUNT_TRANSACTION 참조지만
-- 크로스 도메인이라 FK는 걸지 않는다 (팀 규칙).
-- ============================================================

CREATE TABLE `SETTLEMENT` (
	`settlement_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`job_id`	BIGINT	NOT NULL,
	`period_start`	DATE	NOT NULL,
	`period_end`	DATE	NOT NULL,
	`expected_amount`	BIGINT	NOT NULL	COMMENT '해당 기간 WORK_LOG.estimated_income 합계 스냅샷',
	`actual_amount`	BIGINT	NOT NULL	COMMENT '매칭된 실제 입금액 (ACCOUNT_TRANSACTION.in_amount)',
	`account_transaction_id`	BIGINT	NOT NULL	COMMENT 'account-service 원본 거래 참조, 크로스 도메인이라 FK 없음',
	`matched_at`	DATETIME	NOT NULL,
	PRIMARY KEY (`settlement_id`)
);

ALTER TABLE `SETTLEMENT` ADD CONSTRAINT `FK_JOB_TO_SETTLEMENT_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
);

-- 같은 job의 같은 기간이 두 번 정산 처리되는 것을 막는다 (배치 중복 실행 대비)
ALTER TABLE `SETTLEMENT` ADD CONSTRAINT `UK_SETTLEMENT_JOB_PERIOD` UNIQUE (
	`job_id`, `period_start`, `period_end`
);

CREATE INDEX `IDX_SETTLEMENT_JOB_ID` ON `SETTLEMENT` (`job_id`);
