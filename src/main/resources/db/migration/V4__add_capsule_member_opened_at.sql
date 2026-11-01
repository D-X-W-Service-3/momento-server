-- 멤버별 개봉 확인 시각 (이슈 #41).
-- 같은 열린 캡슐이라도 아직 확인하지 않은 사람에게는 "개봉 가능", 확인한 사람에게는 "개봉됨" 배지가 보인다.
-- 기존 행은 아무도 확인하지 않은 상태라 NULL 로 둔다.
ALTER TABLE `capsule_members` ADD COLUMN `opened_at` DATETIME(6) NULL COMMENT '열린 캡슐을 처음 확인한 일시';
