-- FIN-008: 실제 수집·조회에 사용하는 최소 컬럼만 남긴다.
-- 기존 대출 거래의 총 상환액은 ACCOUNT_TRANSACTION.out_amount로 정규화한다.

ALTER TABLE ACCOUNT
    ADD COLUMN next_payment_date DATE NULL
        COMMENT '다음 적금 납입일 또는 대출 상환일(resDatePayment/추정)'
        AFTER overdraft_yn;

UPDATE ACCOUNT
SET next_payment_date = STR_TO_DATE(payment_date, '%Y%m%d')
WHERE payment_date REGEXP '^[0-9]{8}$';

UPDATE ACCOUNT_TRANSACTION
SET out_amount = CASE
        WHEN transaction_amount IS NOT NULL THEN ABS(transaction_amount)
        ELSE ABS(COALESCE(principal_amount, 0) + COALESCE(interest_amount, 0))
    END
WHERE transaction_category = 'LOAN'
  AND COALESCE(out_amount, 0) = 0;

ALTER TABLE ACCOUNT
    DROP COLUMN account_end_date,
    DROP COLUMN account_lifetime,
    DROP COLUMN loan_kind,
    DROP COLUMN loan_exec_no,
    DROP COLUMN monthly_payment,
    DROP COLUMN interest_rate,
    DROP COLUMN contract_amount,
    DROP COLUMN payment_date,
    DROP COLUMN principal_amount,
    DROP COLUMN overdue_state;

ALTER TABLE ACCOUNT_TRANSACTION
    DROP COLUMN desc1,
    DROP COLUMN installment_round_no,
    DROP COLUMN transaction_type,
    DROP COLUMN transaction_amount,
    DROP COLUMN principal_amount,
    DROP COLUMN interest_amount,
    DROP COLUMN interest_rate;
