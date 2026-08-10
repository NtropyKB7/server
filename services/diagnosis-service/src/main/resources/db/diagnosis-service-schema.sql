CREATE TABLE IF NOT EXISTS `DIAGNOSIS_RESULT`
(
    `diagnosis_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT 'user-service 회원 ID 논리 참조',
    `year_month` CHAR(7) NOT NULL COMMENT '기준연월, YYYY-MM',
    `total_income` BIGINT NOT NULL COMMENT '잡 매칭이 완료된 월 총소득',
    `unmatched_income` BIGINT NOT NULL COMMENT '잡 미매칭 입금 거래 합계',
    `total_expense` BIGINT NOT NULL COMMENT '소비로 분류된 월 총소비',
    `net_cash_flow` BIGINT NOT NULL COMMENT '총소득에서 총소비를 차감한 순현금흐름',
    `fixed_expense` BIGINT NOT NULL COMMENT '고정지출 합계',
    `fixed_expense_ratio` DECIMAL(9,4) NULL COMMENT '총소득 대비 고정지출 비율',
    `total_financial_assets` BIGINT NOT NULL COMMENT '전체 금융자산 합계',
    `liquid_assets` BIGINT NOT NULL COMMENT '즉시 사용할 수 있는 유동자산',
    `safe_assets` BIGINT NOT NULL COMMENT '방어기간에 사용할 수 있는 안전자산',
    `calculated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최근 배치 계산 완료 시각',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_diagnosis_result_user_month` (
                                                    `user_id`,
                                                    `year_month`
                                                )
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4;