-- ============================================================
-- SETTLEMENT.deposit_date 추가
-- 입금 거래가 실제로 발생한 날짜(ACCOUNT_TRANSACTION.transaction_date)를 저장한다.
-- period_start/period_end는 WORK_LOG 기준 근무기간이라 정산주기(MONTHLY 등)에 따라
-- 입금 월과 달라질 수 있어, "몇 월에 입금됐는지"를 구분하려면 별도 컬럼이 필요하다.
-- 이미 배포된 스키마에 대한 증분 추가라 settlement-add-table.sql을 직접 고치지 않는다.
-- ============================================================

ALTER TABLE `SETTLEMENT`
    ADD COLUMN `deposit_date` DATE NOT NULL COMMENT '입금 거래일 (월별 집계 기준)' AFTER `period_end`;
