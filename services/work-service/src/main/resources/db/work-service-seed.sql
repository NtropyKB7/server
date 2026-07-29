-- ============================================================
-- work-service 마스터 데이터 시딩 (CATEGORY, PLATFORM)
-- Issue #2 체크리스트: 카테고리 목록 확정, 플랫폼별 정산주기/정산처명 시드
-- task_per_hour는 JOB 테이블로 이동함 (카테고리가 아니라 잡별로 다를 수 있어서)
-- ============================================================

INSERT INTO `CATEGORY` (category_id, name) VALUES
(1, '배달'),
(2, '대리운전'),
(3, '퀵서비스'),
(4, '택배/물류 상하차'),
(5, '가사·청소 도우미'),
(6, '아르바이트'),
(8, '콜센터·CS상담'),
(9, '펫시터·돌봄'),
(10, '콘텐츠 제작'),
(11, '설문·리서치 참여');

-- deposit_name은 입금 거래내역과 대조되는 실제 정산처명 기준.
-- category_id: 플랫폼이 속한 카테고리 FK (배달의민족/쿠팡이츠/요기요=배달, 카카오T대리=대리운전 등)
-- settlement_cycle 값 예시: DAILY(익일), WEEKLY(주급), MONTHLY(월급)
-- settlement_offset_day는 DAILY 전용, 달력일 기준 단순화 (영업일 계산은 미구현 — 배민 실제론 '3영업일'이라 명절 낀 달은 오차 있음)
-- settlement_day_of_week/settlement_day_of_month: 유튜브(애드센스 매월 21일경)만 실제 확인된 값이고,
--   나머지 WEEKLY/MONTHLY 플랫폼은 검증 안 된 추정값임 — 시연용, 팀 확인 전까지 실제 값으로 취급 금지
-- 콜센터·CS상담(외주) 카테고리는 마땅한 매칭 플랫폼이 없어 시딩 보류
INSERT INTO `PLATFORM`
    (platform_id, category_id, platform_name, deposit_name, settlement_cycle,
     settlement_offset_day, settlement_day_of_week, settlement_day_of_month)
VALUES
(1, 1, '배달의민족', '우아한형제들', 'DAILY', 3, NULL, NULL),
(2, 1, '쿠팡이츠', '쿠팡이츠', 'DAILY', 1, NULL, NULL),
(3, 1, '요기요', '위대한상상', 'DAILY', 1, NULL, NULL),
(4, 2, '카카오T대리', '카카오모빌리티', 'DAILY', 1, NULL, NULL),
(5, 10, '유튜브', '구글코리아', 'MONTHLY', NULL, NULL, 21),
(6, 3, '로지올', '로지올', 'WEEKLY', NULL, 'FRI', NULL),
(7, 4, '쿠팡플렉스', '쿠팡풀필먼트서비스', 'WEEKLY', NULL, 'TUE', NULL),
(8, 5, '미소', '미소', 'WEEKLY', NULL, 'FRI', NULL),
(9, 6, '알바몬', '알바몬', 'MONTHLY', NULL, NULL, 10),
(10, 9, '도그메이트', '도그메이트', 'WEEKLY', NULL, 'MON', NULL),
(11, 11, '패널나우', '엠브레인패널파워', 'MONTHLY', NULL, NULL, 15);
