-- 기념일 생성 · 수정 화면의 메모와 알림 설정을 저장한다 (이슈 #55).
-- 기존 행은 알림을 켠 적이 없으므로 알림 받기는 꺼짐, 알림 시각은 기본값 09:00 으로 채운다.
ALTER TABLE `anniversaries` ADD COLUMN `memo`                 VARCHAR(200) NULL COMMENT '메모' AFTER `repeat_type`;
ALTER TABLE `anniversaries` ADD COLUMN `notification_enabled` BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '알림 받기 여부' AFTER `memo`;
ALTER TABLE `anniversaries` ADD COLUMN `notify_time`          TIME         NOT NULL DEFAULT '09:00:00' COMMENT '알림 시각' AFTER `notification_enabled`;
ALTER TABLE `anniversaries` ADD COLUMN `remind_days_before`   INT          NULL COMMENT '미리 알림 일수: 1, 3, 5, 7, 10' AFTER `notify_time`;

-- 캡슐과 기념일을 잇는 기능이 확정 디자인에 없어 연결 컬럼을 지운다.
-- 기념일을 참조하는 외래키가 없어져 기념일은 행을 바로 지운다.
ALTER TABLE `time_capsules` DROP FOREIGN KEY `FK_TIME_CAPSULES_ANNIVERSARY`;
ALTER TABLE `time_capsules` DROP COLUMN `anniversary_id`;
