-- 알림 설정 화면의 종류별 토글을 저장한다 (이슈 #57).
-- 기존 회원은 새 회원과 같은 기본값을 갖는다. 추억 다시보기는 추천 알림이라 원할 때 켜게 끈다.
ALTER TABLE `users` ADD COLUMN `notify_capsule_opened`  BOOLEAN NOT NULL DEFAULT TRUE  COMMENT '개봉 알림' AFTER `phone_number`;
ALTER TABLE `users` ADD COLUMN `notify_capsule_d_day`   BOOLEAN NOT NULL DEFAULT TRUE  COMMENT 'D-Day 알림' AFTER `notify_capsule_opened`;
ALTER TABLE `users` ADD COLUMN `notify_letter_deadline` BOOLEAN NOT NULL DEFAULT TRUE  COMMENT '수정 마감 알림' AFTER `notify_capsule_d_day`;
ALTER TABLE `users` ADD COLUMN `notify_new_member`      BOOLEAN NOT NULL DEFAULT TRUE  COMMENT '새 참여자 알림' AFTER `notify_letter_deadline`;
ALTER TABLE `users` ADD COLUMN `notify_memory_reminder` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '추억 다시보기' AFTER `notify_new_member`;

-- 전체 알림 스위치는 화면에 없고, 종류별 설정과 기기 알림 권한이 그 역할을 한다.
ALTER TABLE `users` DROP COLUMN `notification_enabled`;
