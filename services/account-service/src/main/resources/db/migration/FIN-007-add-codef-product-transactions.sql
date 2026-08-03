-- FIN-007: 이슈 #38 CODEF 적금·대출 필드를 기존 계좌·거래 테이블에 통합하고
-- 수시입출 거래 중복 방지를 적용한다. account-service-schema.sql로 새 DB를 만든 경우에는 실행하지 않는다.

UPDATE ACCOUNT
SET balance = CASE
        WHEN loan_balance IS NOT NULL THEN ABS(loan_balance)
        WHEN balance < 0 THEN ABS(balance)
        ELSE 0
    END,
    account_start_date = COALESCE(loan_start_date, account_start_date),
    account_end_date = COALESCE(loan_end_date, account_end_date)
WHERE account_group = 'DEPOSIT_TRUST'
  AND COALESCE(overdraft_yn, FALSE) = TRUE;

UPDATE ACCOUNT
SET balance = ABS(balance)
WHERE account_group = 'LOAN'
  AND balance IS NOT NULL;

ALTER TABLE ACCOUNT
    DROP COLUMN loan_balance,
    DROP COLUMN loan_start_date,
    DROP COLUMN loan_end_date,
    DROP COLUMN invested_cost,
    DROP COLUMN earnings_rate;

ALTER TABLE ACCOUNT
    ADD COLUMN monthly_payment DECIMAL(18,2) NULL COMMENT '적금 resMonthlyPayment' AFTER loan_exec_no,
    ADD COLUMN interest_rate DECIMAL(9,4) NULL COMMENT '적금·대출 resRate' AFTER monthly_payment,
    ADD COLUMN contract_amount DECIMAL(18,2) NULL COMMENT '적금 resContractAmount' AFTER interest_rate,
    ADD COLUMN payment_date VARCHAR(20) NULL COMMENT '대출 resDatePayment' AFTER contract_amount,
    ADD COLUMN principal_amount DECIMAL(18,2) NULL COMMENT '대출 resPrincipal 약정원금' AFTER payment_date,
    ADD COLUMN overdue_state VARCHAR(50) NULL COMMENT '대출 resState' AFTER principal_amount;

ALTER TABLE ACCOUNT_TRANSACTION
    ADD COLUMN fingerprint CHAR(64) NULL
        COMMENT '계좌·거래일시·상품별 금액·상세 기반 SHA-256'
        AFTER account_id,
    ADD COLUMN transaction_category VARCHAR(20) NOT NULL DEFAULT 'ORDINARY'
        COMMENT 'ORDINARY, INSTALLMENT, LOAN'
        AFTER fingerprint,
    ADD COLUMN installment_round_no VARCHAR(20) NULL COMMENT '적금 resRoundNo' AFTER desc4,
    ADD COLUMN transaction_type VARCHAR(100) NULL COMMENT '대출 resTransTypeNm' AFTER installment_round_no,
    ADD COLUMN transaction_amount DECIMAL(18,2) NULL COMMENT '대출 resTranAmount' AFTER transaction_type,
    ADD COLUMN principal_amount DECIMAL(18,2) NULL COMMENT '대출 원금 상환액' AFTER transaction_amount,
    ADD COLUMN interest_amount DECIMAL(18,2) NULL COMMENT '대출 이자 납입액' AFTER principal_amount,
    ADD COLUMN interest_rate DECIMAL(9,4) NULL COMMENT '대출 resInterestRate' AFTER interest_amount,
    MODIFY COLUMN tran_date DATE NULL,
    MODIFY COLUMN after_balance DECIMAL(18,2) NULL;

UPDATE ACCOUNT_TRANSACTION
SET fingerprint = SHA2(CONCAT_WS(CHAR(31),
        account_id,
        DATE_FORMAT(tran_date, '%Y-%m-%d'),
        COALESCE(TIME_FORMAT(tran_time, '%H:%i:%s'), '<null>'),
        TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(out_amount AS CHAR))),
        TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(in_amount AS CHAR))),
        TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(after_balance AS CHAR))),
        COALESCE(TRIM(desc1), '<null>'),
        COALESCE(TRIM(desc2), '<null>'),
        COALESCE(TRIM(desc3), '<null>'),
        COALESCE(TRIM(desc4), '<null>')
    ), 256)
WHERE fingerprint IS NULL;

DELETE duplicate_row
FROM ACCOUNT_TRANSACTION duplicate_row
JOIN ACCOUNT_TRANSACTION keeper
  ON keeper.account_id = duplicate_row.account_id
 AND keeper.fingerprint = duplicate_row.fingerprint
 AND keeper.id < duplicate_row.id;

ALTER TABLE ACCOUNT_TRANSACTION
    MODIFY COLUMN fingerprint CHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_account_transaction_fingerprint (account_id, fingerprint);
