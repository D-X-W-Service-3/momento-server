-- 알림 발송의 중복 방지 키 (이슈 #65).
-- 날짜가 기준인 알림(D-Day · 수정 마감 · 추억 다시보기 · 기념일)은 1분 주기 스케줄러가 보낸다.
-- 서버가 재시작되면 그날 0시부터 다시 훑으므로, 이미 보낸 알림은 이 키로 건너뛴다.
-- NULL 은 여러 개 둘 수 있어 키가 없는 알림은 중복을 막지 않는다.
ALTER TABLE `notifications` ADD COLUMN `dedup_key` VARCHAR(100) NULL COMMENT '중복 방지 키. 회원마다 유일하다' AFTER `content`;
ALTER TABLE `notifications` ADD CONSTRAINT `UK_NOTIFICATIONS_USER_ID_DEDUP_KEY` UNIQUE (`user_id`, `dedup_key`);
