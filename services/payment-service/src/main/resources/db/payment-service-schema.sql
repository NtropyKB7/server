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
                                `status`	VARCHAR(20)	NULL
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
                           `receipt_url`	VARCHAR(500)	NULL
);

-- Primary Key

ALTER TABLE `SUBSCRIPTION` ADD CONSTRAINT `PK_SUBSCRIPTION` PRIMARY KEY (
                                                                         `subscription_id`
    );

ALTER TABLE `PAYMENT` ADD CONSTRAINT `PK_PAYMENT` PRIMARY KEY (
                                                               `payment_id`
    );

-- Foreign Key (payment-service 내부 참조만 - 서비스 내부는 FK 유지)

ALTER TABLE `PAYMENT` ADD CONSTRAINT `FK_SUBSCRIPTION_TO_PAYMENT_1` FOREIGN KEY (
                                                                                 `subscription_id`
    )
    REFERENCES `SUBSCRIPTION` (
                               `subscription_id`
        );

-- Unique Constraint

-- 컬럼 주석("UNIQUE")에 명시된 대로 실제 제약을 건다. SUBSCRIPTION02_F05(중복결제 방지)의 DB 레벨 최후 방어선.
ALTER TABLE `PAYMENT` ADD CONSTRAINT `UQ_PAYMENT_MERCHANT_UID` UNIQUE (`merchant_uid`);

-- 빌링키(customer_uid) 중복 방지. MySQL UNIQUE INDEX는 NULL을 여러 개 허용하므로
-- 결제수단 미등록 상태(customer_uid IS NULL)와는 충돌하지 않음.
ALTER TABLE `SUBSCRIPTION` ADD CONSTRAINT `UQ_SUBSCRIPTION_CUSTOMER_UID` UNIQUE (`customer_uid`);

-- Index (work-service 컨벤션과 동일: FK 걸린 컬럼도 명시적으로 인덱스 추가)
CREATE INDEX `IDX_PAYMENT_SUBSCRIPTION_ID` ON `PAYMENT` (`subscription_id`);
-- user_id는 크로스 도메인(user-service)이라 FK는 걸지 않되, 조회 성능을 위해 인덱스는 추가
CREATE INDEX `IDX_SUBSCRIPTION_USER_ID` ON `SUBSCRIPTION` (`user_id`);