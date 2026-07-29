-- ============================================================
-- 전체 모듈 DDL을 모아서 순서대로 실행하는 스크립트
-- 로컬 DB 최초 세팅 시 레포 루트에서: mysql -u <user> -p <db> < db/init-all.sql
-- 각 모듈 담당자는 본인 모듈 완료 시 SOURCE 라인 주석 해제
-- 실행 순서: 참조 관계상 문제 없는 서비스부터 (user -> account/work -> diagnosis -> defense -> payment -> notification -> AI)
-- ============================================================

-- user-service
-- SOURCE services/user-service/src/main/resources/db/user-service-schema.sql;

-- account-service
-- SOURCE services/account-service/src/main/resources/db/account-service-schema.sql;

-- work-service
SOURCE services/work-service/src/main/resources/db/work-service-schema.sql;
SOURCE services/work-service/src/main/resources/db/work-service-seed.sql;

-- diagnosis-service
-- SOURCE services/diagnosis-service/src/main/resources/db/diagnosis-service-schema.sql;

-- defense-service
-- SOURCE services/defense-service/src/main/resources/db/defense-service-schema.sql;

-- payment-service
-- SOURCE services/payment-service/src/main/resources/db/payment-service-schema.sql;

-- notification-service
-- SOURCE services/notification-service/src/main/resources/db/notification-service-schema.sql;

-- bff-service (해당 없으면 스킵)
-- SOURCE services/bff-service/src/main/resources/db/bff-service-schema.sql;
