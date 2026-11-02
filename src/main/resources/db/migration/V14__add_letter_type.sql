-- 편지 유형. 기존 편지는 NULL 로 두고 새 작성 API 에서만 필수로 검증한다.
ALTER TABLE `letters` ADD COLUMN `letter_type` VARCHAR(20) NULL COMMENT '편지 유형: BIRTHDAY, COUPLE_ANNIVERSARY, CHRISTMAS, GENERAL. 기존 편지는 NULL' AFTER `title`;
