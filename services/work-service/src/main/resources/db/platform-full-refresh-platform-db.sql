-- ============================================================
-- 이미 RDS에 반영된 PLATFORM 마스터 데이터를 "플랫폼 DB" 문서 기준으로 전면 교체.
-- work-service-seed.sql은 최초 세팅용이라 재실행하면 PK 충돌이 나므로,
-- 이미 배포된 DB에는 이 파일로 반영한다.
--
-- 기존 6개(1~5,7) 대비 값이 달라진 것도 있고(예: 배민 정산처 우아한형제들→우아한청년들,
-- 유튜브 정산처 구글코리아→GOOGLE) 신규 플랫폼 9개가 추가되어 병합 대신 전체 재삽입한다.
-- category_id(1,2,4,5,9,10)는 work-service-seed.sql의 CATEGORY 시드에 이미 존재함.
-- settlement_trigger_type: 카카오T대리/티맵 대리만 ON_DEMAND(포인트 적립 후 사용자 출금 신청
--   방식이라 실거래 매칭 불가 - WorkLog 확정 시 즉시 정산완료 처리), 나머지는 AUTO.
--
-- 주의: JOBPLATFORMMAPPING을 먼저 지우기 때문에, 이 스크립트를 실행하면 기존에 온보딩된
-- 사용자의 잡-플랫폼 매핑이 모두 사라진다. 시연 전 테스트 데이터 정리 목적이 아니라면
-- 실행 전 반드시 확인할 것.
-- ============================================================

START TRANSACTION;

DELETE FROM `JOBPLATFORMMAPPING`;
DELETE FROM `PLATFORM`;

INSERT INTO `PLATFORM`
    (platform_id, category_id, platform_name, deposit_name, settlement_cycle, settlement_trigger_type,
     settlement_offset_day, settlement_offset_unit, settlement_day_of_week, settlement_day_of_month)
VALUES
(1, 1, '배민커넥트', '우아한청년들', 'DAILY', 'AUTO', 3, 'BUSINESS_DAY', NULL, NULL),
(2, 1, '쿠팡이츠 배달파트너', '쿠팡이츠정산', 'WEEKLY', 'AUTO', 3, 'BUSINESS_DAY', 'FRI', NULL),
(3, 1, '요기요라이더', '위대한상상', 'WEEKLY', 'AUTO', 15, 'CALENDAR_DAY', 'MON', NULL),
(4, 2, '카카오T대리', '카카오모빌리티', 'DAILY', 'ON_DEMAND', 1, 'CALENDAR_DAY', NULL, NULL),
(5, 10, '유튜브', 'GOOGLE', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 21),
(7, 4, '쿠팡플렉스', '쿠팡-용역비', 'WEEKLY', 'AUTO', 4, 'CALENDAR_DAY', 'FRI', NULL),
(8, 2, '티맵 대리', '티맵모빌리티', 'DAILY', 'ON_DEMAND', 1, 'CALENDAR_DAY', NULL, NULL),
(9, 4, 'CJ대한통운 상하차', 'CJ대한통운', 'DAILY', 'AUTO', 1, 'CALENDAR_DAY', NULL, NULL),
(10, 4, '로젠택배', '로젠택배', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 5),
(11, 5, '청소연구소(청연)', '생활연구소', 'DAILY', 'AUTO', 1, 'CALENDAR_DAY', NULL, NULL),
(12, 5, '미소', '유한회사미소', 'DAILY', 'AUTO', 1, 'CALENDAR_DAY', NULL, NULL),
(13, 9, '케어닥', '케어닥', 'WEEKLY', 'AUTO', 1, 'CALENDAR_DAY', 'MON', NULL),
(14, 9, '펫플래닛', '펫피플', 'WEEKLY', 'AUTO', 3, 'CALENDAR_DAY', 'WED', NULL),
(15, 10, '틱톡', 'PAYPAL', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 15),
(16, 10, '네이버TV', '네이버', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 6);

COMMIT;
