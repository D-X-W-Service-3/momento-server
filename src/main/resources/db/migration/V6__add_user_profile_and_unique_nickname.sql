-- 회원 프로필 필드와 닉네임 중복 금지 (이슈 #43).
-- 프로필 정보 수정 화면에 생일 · 이메일 · 전화번호가 있다. 기존 회원은 입력한 적이 없으므로 NULL 로 둔다.
ALTER TABLE `users` ADD COLUMN `birthday`     DATE         NULL COMMENT '생일' AFTER `profile_image_url`;
ALTER TABLE `users` ADD COLUMN `email`        VARCHAR(100) NULL COMMENT '이메일' AFTER `birthday`;
ALTER TABLE `users` ADD COLUMN `phone_number` VARCHAR(20)  NULL COMMENT '전화번호 (010-1234-5678)' AFTER `email`;

-- 기존 중복 닉네임을 정리한다. 닉네임마다 가장 먼저 가입한 회원(가장 작은 ID)만 그대로 두고,
-- 나머지는 뒤에 _{id} 를 붙인다. 컬럼이 30자라 앞부분을 잘라 30자 안에 맞춘다.
-- MySQL 은 UPDATE 대상 테이블을 같은 문장의 서브쿼리에서 바로 읽지 못해(1093) 파생 테이블로 한 번 감싼다.
UPDATE `users`
SET `nickname` = CONCAT(LEFT(`nickname`, 29 - CHAR_LENGTH(CONCAT(`id`, ''))), '_', `id`)
WHERE `id` NOT IN (SELECT `keep_id` FROM (SELECT MIN(`id`) AS `keep_id` FROM `users` GROUP BY `nickname`) `k`);

ALTER TABLE `users` ADD CONSTRAINT `UK_USERS_NICKNAME` UNIQUE (`nickname`);
