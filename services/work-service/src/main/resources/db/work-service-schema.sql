-- ============================================================
-- work-service 소유 테이블 DDL
-- 소유 서비스: work-service
-- 대상: Category, PLATFORM, JOB, JOB_SCHEDULE, WORK_LOG, ALLOCATION_GOAL
-- 생성 순서: 참조 관계상 부모 -> 자식 순
-- 주의: user_id는 user-service USER를 참조하지만 크로스 도메인 FK는 걸지 않음 (팀 규칙)
-- ============================================================

-- 1. Category (마스터 데이터)
CREATE TABLE `Category` (
	`category_id`	BIGINT	NOT NULL,
	`name`	VARCHAR(50)	NULL,
	`task_per_hour`	FLOAT	NULL
);

-- 2. PLATFORM (플랫폼 마스터 - 잡 등록 시 라벨링에 사용)
CREATE TABLE `PLATFORM` (
	`platform_id`	BIGINT	NOT NULL,
	`job_id`	BIGINT	NOT NULL,
	`platform_name`	VARCHAR(50)	NOT NULL	COMMENT '플랫폼명 (배달의민족/카카오T대리 등)',
	`deposit_name`	VARCHAR(100)	NULL,
	`settlement_cycle`	VARCHAR(20)	NULL,
	`is_active`	BOOLEAN	NOT NULL	DEFAULT TRUE,
	`settlement_day`	VARCHAR(20)	NULL
);

-- 3. JOB (잡 등록)
CREATE TABLE `JOB` (
	`job_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`reward_type`	VARCHAR(20)	NULL,
	`hourly_wage`	INT	NULL,
	`per_task_amount`	INT	NULL,
	`schedule_type`	VARCHAR(20)	NULL,
	`base_fatigue`	INT	NULL,
	`created_at`	DATETIME	NULL,
	`updated_at`	DATETIME	NULL,
	`is_active`	BOOLEAN	NULL
);

-- 4. JOB_SCHEDULE (정기 근무 스케줄)
CREATE TABLE `JOB_SCHEDULE` (
	`schedule_id`	BIGINT	NOT NULL,
	`job_id`	BIGINT	NOT NULL,
	`day_of_week`	VARCHAR(10)	NULL,
	`start_time`	VARCHAR(10)	NULL,
	`end_time`	VARCHAR(10)	NULL
);

-- 5. WORK_LOG (근무 계획/실적 기록)
CREATE TABLE `WORK_LOG` (
	`log_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`job_id`	BIGINT	NOT NULL,
	`work_date`	DATE	NULL,
	`start_time`	TIME	NULL,
	`end_time`	TIME	NULL,
	`fatigue`	BIGINT	NULL,
	`estimated_income`	BIGINT	NULL,
	`status`	VARCHAR(20)	NULL
);

-- 6. ALLOCATION_GOAL (잡별 근무시간 배분 추천)
CREATE TABLE `ALLOCATION_GOAL` (
	`allocation_goal_id`	BIGINT	NOT NULL,
	`job_id`	BIGINT	NOT NULL,
	`target_month`	VARCHAR(7)	NULL,
	`recommend_hour`	BIGINT	NULL
);

-- ============================================================
-- Primary Key
-- ============================================================

ALTER TABLE `Category` ADD CONSTRAINT `PK_CATEGORY` PRIMARY KEY (
	`category_id`
);

ALTER TABLE `PLATFORM` ADD CONSTRAINT `PK_PLATFORM` PRIMARY KEY (
	`platform_id`
);

ALTER TABLE `JOB` ADD CONSTRAINT `PK_JOB` PRIMARY KEY (
	`job_id`
);

ALTER TABLE `JOB_SCHEDULE` ADD CONSTRAINT `PK_JOB_SCHEDULE` PRIMARY KEY (
	`schedule_id`
);

ALTER TABLE `WORK_LOG` ADD CONSTRAINT `PK_WORK_LOG` PRIMARY KEY (
	`log_id`
);

ALTER TABLE `ALLOCATION_GOAL` ADD CONSTRAINT `PK_ALLOCATION_GOAL` PRIMARY KEY (
	`allocation_goal_id`
);

-- ============================================================
-- Foreign Key (work-service 내부 참조만 - 서비스 내부는 FK 유지)
-- ============================================================

ALTER TABLE `JOB` ADD CONSTRAINT `FK_CATEGORY_TO_JOB_1` FOREIGN KEY (
	`category_id`
)
REFERENCES `Category` (
	`category_id`
);

ALTER TABLE `PLATFORM` ADD CONSTRAINT `FK_JOB_TO_PLATFORM_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
);

ALTER TABLE `JOB_SCHEDULE` ADD CONSTRAINT `FK_JOB_TO_JOB_SCHEDULE_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
);

ALTER TABLE `WORK_LOG` ADD CONSTRAINT `FK_JOB_TO_WORK_LOG_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
);

ALTER TABLE `ALLOCATION_GOAL` ADD CONSTRAINT `FK_JOB_TO_ALLOCATION_GOAL_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
);

-- ============================================================
-- Index (job_id 기준 - 체크리스트 항목)
-- ============================================================

CREATE INDEX `IDX_PLATFORM_JOB_ID` ON `PLATFORM` (`job_id`);
CREATE INDEX `IDX_JOB_SCHEDULE_JOB_ID` ON `JOB_SCHEDULE` (`job_id`);
CREATE INDEX `IDX_WORK_LOG_JOB_ID` ON `WORK_LOG` (`job_id`);
CREATE INDEX `IDX_ALLOCATION_GOAL_JOB_ID` ON `ALLOCATION_GOAL` (`job_id`);

-- user_id는 크로스 도메인(user-service)이라 FK는 걸지 않되, 조회 성능을 위해 인덱스는 추가
CREATE INDEX `IDX_JOB_USER_ID` ON `JOB` (`user_id`);
CREATE INDEX `IDX_WORK_LOG_USER_ID` ON `WORK_LOG` (`user_id`);

-- 캘린더 월별 조회 시 자주 쓰이는 조합이라 함께 추가
CREATE INDEX `IDX_WORK_LOG_USER_DATE` ON `WORK_LOG` (`user_id`, `work_date`);
