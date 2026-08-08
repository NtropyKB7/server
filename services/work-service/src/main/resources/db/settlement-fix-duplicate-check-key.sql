-- ============================================================
-- SETTLEMENT 중복 방지 기준을 (job_id, period_start, period_end)에서
-- account_transaction_id로 교체.
-- 기존 UK_SETTLEMENT_JOB_PERIOD는 "같은 잡·같은 정산기간에 서로 다른 거래
-- 2건이 들어오는" 정상 케이스까지 막아버리는 문제가 있었다. 진짜 막아야
-- 하는 건 "같은 거래(transactionId)를 두 번 처리하는 것"이므로 이 기준으로
-- 바꾼다. UNMATCHED 행은 account_transaction_id가 NULL인데, MySQL UNIQUE
-- 인덱스는 NULL을 서로 다른 값으로 취급하므로 여러 UNMATCHED 행이 있어도
-- 이 제약에 걸리지 않는다.
-- ============================================================

ALTER TABLE `SETTLEMENT` DROP INDEX `UK_SETTLEMENT_JOB_PERIOD`;

ALTER TABLE `SETTLEMENT` ADD CONSTRAINT `UK_SETTLEMENT_ACCOUNT_TRANSACTION_ID` UNIQUE (
	`account_transaction_id`
);
