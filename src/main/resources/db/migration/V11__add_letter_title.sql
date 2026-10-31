-- 기존 편지의 제목·상태·본문·화풍은 보존한다. 새 작성 API에서만 제목을 필수로 검증한다.
ALTER TABLE `letters` ADD COLUMN `title` VARCHAR(50) NULL COMMENT '편지 제목. 기존 편지는 NULL' AFTER `author_id`;
