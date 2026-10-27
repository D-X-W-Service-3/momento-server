-- 초대 링크를 처음 연 회원 (이슈 #42).
-- 공유 링크는 발급할 때 대상이 없고, 로그인한 회원이 처음 열면 그 회원에게 고정된다.
-- 탭 홈의 "참여 요청받은 타임캡슐이 있어요" 배너는 나에게 고정된 ACTIVE 초대가 있는지로 판단한다.
-- 기존 행은 아무도 열지 않은 상태라 NULL 로 둔다.
ALTER TABLE `capsule_invites` ADD COLUMN `invitee_id` BIGINT NULL COMMENT '이 링크를 처음 연 회원 ID. 발급 시점에는 NULL' AFTER `inviter_id`;

-- 탭 홈이 회원별로 대기 중인 초대를 찾는 조회다. 등호 조건인 invitee_id, status 순이다.
CREATE INDEX `IDX_CAPSULE_INVITES_INVITEE_ID_STATUS` ON `capsule_invites` (`invitee_id`, `status`);

ALTER TABLE `capsule_invites` ADD CONSTRAINT `FK_CAPSULE_INVITES_INVITEE` FOREIGN KEY (`invitee_id`) REFERENCES `users` (`id`);
