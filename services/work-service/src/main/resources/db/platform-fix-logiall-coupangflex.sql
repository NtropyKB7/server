-- ============================================================
-- 이미 RDS에 반영된 PLATFORM 시드 데이터 수정
-- work-service-seed.sql은 최초 세팅용이라 재실행하면 PK 충돌이 나므로,
-- 이미 배포된 DB에는 이 파일로 반영한다.
--
-- 1) 로지올(platform_id=6): 실제로는 실시간 정산 + 온디맨드 출금 구조라
--    WEEKLY 모델과 안 맞아 제거함. 참조하는 JOBPLATFORMMAPPING/mock 데이터 없음(확인됨).
-- 2) 쿠팡플렉스(platform_id=7): 공식 사이트 확인 결과 실제 offset은 4
--    (화~차주 월 배송분을 차주 월 기준 D+4일 금요일 정산). settlement_day_of_week는
--    "정산(입금)이 되는 날"이라는 의미로 재정의해 FRI로 수정.
-- ============================================================

DELETE FROM `PLATFORM` WHERE `platform_id` = 6;

UPDATE `PLATFORM` SET `settlement_offset_day` = 4, `settlement_day_of_week` = 'FRI' WHERE `platform_id` = 7;
