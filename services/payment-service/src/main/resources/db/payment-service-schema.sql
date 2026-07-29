DROP TABLE IF EXISTS `PAYMENT`;
DROP TABLE IF EXISTS `SUBSCRIPTION`;

-- 1. SUBSCRIPTION (구독 상태 / 결제수단)
CREATE TABLE `SUBSCRIPTION` (
                                `subscription_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
                                `user_id`	BIGINT	NOT NULL,
                                `plan_code`	VARCHAR(20)	NULL,
                                `start_date`	DATETIME	NULL,
                                `end_date`	DATETIME	NULL,
                                `cancel_requested_at`	DATETIME	NULL,
                                `auto_renew_yn`	BOOLEAN	NULL,
                                `customer_uid`	VARCHAR(50)	NULL,
                                `card_name`	VARCHAR(50)	NULL,
                                `card_number_masked`	VARCHAR(30)	NULL,
                                `status`	VARCHAR(20)	NULL,
                                PRIMARY KEY (`subscription_id`)
);

-- 2. PAYMENT (결제 내역)
CREATE TABLE `PAYMENT` (
                           `payment_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
                           `subscription_id`	BIGINT	NOT NULL,
                           `plan_code`	VARCHAR(20)	NULL,
                           `imp_uid`	VARCHAR(50)	NULL,
                           `amount`	BIGINT	NULL,
                           `payment_method`	VARCHAR(20)	NULL,
                           `created_at`	DATETIME	NULL,
                           `merchant_uid`	VARCHAR(50)	NULL	COMMENT 'UNIQUE',
                           `payment_status`	VARCHAR(20)	NULL,
                           `receipt_url`	VARCHAR(500)	NULL,
                           PRIMARY KEY (`payment_id`)
);

-- ============================================================
-- Foreign Key (payment-service 내부 참조만 - 서비스 내부는 FK 유지)
-- ============================================================

ALTER TABLE `PAYMENT` ADD CONSTRAINT `FK_SUBSCRIPTION_TO_PAYMENT_1` FOREIGN KEY (
                                                                                 `subscription_id`
    )
    REFERENCES `SUBSCRIPTION` (
                               `subscription_id`
        );

-- ============================================================
-- Unique Constraint
-- ============================================================

ALTER TABLE `PAYMENT` ADD CONSTRAINT `UQ_PAYMENT_MERCHANT_UID` UNIQUE (`merchant_uid`);

ALTER TABLE `SUBSCRIPTION` ADD CONSTRAINT `UQ_SUBSCRIPTION_CUSTOMER_UID` UNIQUE (`customer_uid`);

-- ============================================================
-- Index
-- ============================================================

CREATE INDEX `IDX_PAYMENT_SUBSCRIPTION_ID` ON `PAYMENT` (`subscription_id`);

CREATE INDEX `IDX_SUBSCRIPTION_USER_ID` ON `SUBSCRIPTION` (`user_id`);


-- 케이스 1: PRO, 정상 이용중 (userId = 101)
INSERT INTO SUBSCRIPTION (user_id, plan_code, status, start_date, end_date, auto_renew_yn, card_name, card_number_masked)
VALUES (101, 'PRO', 'ACTIVE', '2026-07-16 00:00:00', '2026-08-16 00:00:00', 1, '신한카드', '**** 1111');

-- 케이스 2: PRO, 해지예정 (userId = 102)
INSERT INTO SUBSCRIPTION (user_id, plan_code, status, start_date, end_date, auto_renew_yn, cancel_requested_at, card_name, card_number_masked)
VALUES (102, 'PRO', 'CANCEL_SCHEDULED', '2026-06-16 00:00:00', '2026-08-16 00:00:00', 0, '2026-07-20 12:00:00', '신한카드', '**** 1111');

-- 케이스 3: Basic을 DB에 명시적으로 저장해둔 경우 (userId = 103)
INSERT INTO SUBSCRIPTION (user_id, plan_code, status, auto_renew_yn)
VALUES (103, 'BASIC', 'ACTIVE', 0);

-- 케이스 4(구독없음)는 INSERT 필요없음 - 그냥 위에 없는 숫자(예: 999)로 조회하면 됨