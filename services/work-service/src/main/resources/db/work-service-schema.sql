-- ============================================================
-- work-service 소유 테이블 DDL
-- 소유 서비스: work-service
-- 대상: CATEGORY, PLATFORM, JOBPLATFORMMAPPING, JOB, JOB_SCHEDULE, WORK_LOG, ALLOCATION_GOAL
-- 생성 순서: 참조 관계상 부모 -> 자식 순
-- 주의: user_id는 user-service USER를 참조하지만 크로스 도메인 FK는 걸지 않음 (팀 규칙)
-- ============================================================

-- 1. CATEGORY (마스터 데이터)
CREATE TABLE `CATEGORY` (
	`category_id`	BIGINT	NOT NULL,
	`name`	VARCHAR(50)	NOT NULL
);

-- 2. PLATFORM (플랫폼 마스터 - 사용자 가입 이전에 미리 시딩되는 참조 데이터)
CREATE TABLE `PLATFORM` (
	`platform_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`platform_name`	VARCHAR(50)	NOT NULL	COMMENT '플랫폼명 (배달의민족/카카오T대리 등)',
	`deposit_name`	VARCHAR(100)	NOT NULL	COMMENT '입금 거래내역 대조용 입금처명',
	`settlement_cycle`	VARCHAR(20)	NOT NULL	COMMENT '정산주기 (DAILY/WEEKLY/MONTHLY)',
	`settlement_offset_day`	INT	NULL	COMMENT 'DAILY 전용: 정산까지 며칠 (달력일 기준, 예: 익일=1)',
	`settlement_day_of_week`	VARCHAR(20)	NULL	COMMENT 'WEEKLY 전용: MON~SUN',
	`settlement_day_of_month`	INT	NULL	COMMENT 'MONTHLY 전용: 1~31'
);

-- 3. JOBPLATFORMMAPPING (사용자별 잡-플랫폼 연결 인스턴스)
CREATE TABLE `JOBPLATFORMMAPPING` (
	`mapping_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`job_id`	BIGINT	NOT NULL,
	`platform_id`	BIGINT	NOT NULL
);

-- 4. JOB (잡 등록)
CREATE TABLE `JOB` (
	`job_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`job_name`	VARCHAR(50)	NOT NULL	COMMENT '사용자가 직접 입력하는 잡 라벨 (카테고리만으로 구분 안 되는 실제 근무지명)',
	`settlement_type`	VARCHAR(20)	NOT NULL,
	`hourly_wage`	INT	NULL,
	`monthly_wage`	INT	NULL,
	`per_task_wage`	INT	NULL,
	`task_per_hour`	FLOAT	NULL,
	`is_regular`	BOOLEAN	NOT NULL,
	`base_fatigue`	INT	NOT NULL,
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL,
	`is_active`	BOOLEAN	NOT NULL
);

-- 5. JOB_SCHEDULE (정기 근무 스케줄)
CREATE TABLE `JOB_SCHEDULE` (
	`schedule_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`job_id`	BIGINT	NOT NULL,
	`day_of_week`	VARCHAR(10)	NULL,
	`start_time`	TIME	NULL,
	`end_time`	TIME	NULL
);

-- 6. WORK_LOG (근무 계획/실적 기록)
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

-- 7. ALLOCATION_GOAL (잡별 근무시간 배분 추천)
CREATE TABLE `ALLOCATION_GOAL` (
	`allocation_goal_id`	BIGINT	NOT NULL,
	`job_id`	BIGINT	NOT NULL,
	`target_month`	VARCHAR(7)	NULL,
	`recommend_hour`	BIGINT	NULL
);

-- ============================================================
-- Primary Key
-- ============================================================

ALTER TABLE `CATEGORY` ADD CONSTRAINT `PK_CATEGORY` PRIMARY KEY (
	`category_id`
);

ALTER TABLE `PLATFORM` ADD CONSTRAINT `PK_PLATFORM` PRIMARY KEY (
	`platform_id`
);

ALTER TABLE `JOBPLATFORMMAPPING` ADD CONSTRAINT `PK_JOBPLATFORMMAPPING` PRIMARY KEY (
	`mapping_id`
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
REFERENCES `CATEGORY` (
	`category_id`
);

ALTER TABLE `PLATFORM` ADD CONSTRAINT `FK_CATEGORY_TO_PLATFORM_1` FOREIGN KEY (
	`category_id`
)
REFERENCES `CATEGORY` (
	`category_id`
);

ALTER TABLE `JOBPLATFORMMAPPING` ADD CONSTRAINT `FK_JOB_TO_JOBPLATFORMMAPPING_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
);

ALTER TABLE `JOBPLATFORMMAPPING` ADD CONSTRAINT `FK_PLATFORM_TO_JOBPLATFORMMAPPING_1` FOREIGN KEY (
	`platform_id`
)
REFERENCES `PLATFORM` (
	`platform_id`
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

CREATE INDEX `IDX_JOBPLATFORMMAPPING_JOB_ID` ON `JOBPLATFORMMAPPING` (`job_id`);
CREATE INDEX `IDX_JOBPLATFORMMAPPING_MASTER_ID` ON `JOBPLATFORMMAPPING` (`platform_id`);
CREATE INDEX `IDX_PLATFORM_CATEGORY_ID` ON `PLATFORM` (`category_id`);
CREATE INDEX `IDX_JOB_SCHEDULE_JOB_ID` ON `JOB_SCHEDULE` (`job_id`);
CREATE INDEX `IDX_WORK_LOG_JOB_ID` ON `WORK_LOG` (`job_id`);
CREATE INDEX `IDX_ALLOCATION_GOAL_JOB_ID` ON `ALLOCATION_GOAL` (`job_id`);

-- user_id는 크로스 도메인(user-service)이라 FK는 걸지 않되, 조회 성능을 위해 인덱스는 추가
CREATE INDEX `IDX_JOB_USER_ID` ON `JOB` (`user_id`);
CREATE INDEX `IDX_WORK_LOG_USER_ID` ON `WORK_LOG` (`user_id`);

-- 캘린더 월별 조회 시 자주 쓰이는 조합이라 함께 추가
CREATE INDEX `IDX_WORK_LOG_USER_DATE` ON `WORK_LOG` (`user_id`, `work_date`);
