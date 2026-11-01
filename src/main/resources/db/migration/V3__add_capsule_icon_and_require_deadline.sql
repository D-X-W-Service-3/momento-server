-- 타임캡슐 생성을 확정 디자인에 맞춘다 (이슈 #40).
-- 아이콘을 추가하고, 모든 캡슐이 편지 수정 마감 일시를 갖게 한다.
-- 기존 행은 테스트 데이터뿐이라 단순한 기본값으로 채운다.

-- 캡슐 대표 아이콘. 값 목록이 확정되지 않아 문자열로 둔다.
ALTER TABLE `time_capsules` ADD COLUMN `icon_type` VARCHAR(30) NULL COMMENT '캡슐 대표 아이콘';
UPDATE `time_capsules` SET `icon_type` = 'ICE_CREAM' WHERE `icon_type` IS NULL;
ALTER TABLE `time_capsules` MODIFY COLUMN `icon_type` VARCHAR(30) NOT NULL COMMENT '캡슐 대표 아이콘';

-- 마감 일시가 없던 캡슐은 공개 하루 전 23:59:59 에 잠기게 한다.
UPDATE `time_capsules`
SET `letter_deadline_at` = TIMESTAMPADD(SECOND, -1, CAST(CAST(`open_at` AS DATE) AS DATETIME))
WHERE `letter_deadline_at` IS NULL;
ALTER TABLE `time_capsules` MODIFY COLUMN `letter_deadline_at` DATETIME(6) NOT NULL COMMENT '편지 작성 마감 일시';
