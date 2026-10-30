-- 초대 링크를 연 회원별 대기 기록 (이슈 #42).
-- 공유 링크 하나를 여러 사람이 열 수 있어서, 링크가 아니라 (링크, 회원) 마다 한 행을 둔다.
-- 링크를 열면 PENDING 으로 만들고, 수락하면 ACCEPTED, 거절하면 DECLINED 로 바꾼다.
-- 탭 홈의 "참여 요청받은 타임캡슐이 있어요" 배너와 받은 초대 목록이 PENDING 행을 읽는다.
CREATE TABLE `capsule_invitees` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '초대 대상 ID',
    `capsule_invite_id` BIGINT      NOT NULL COMMENT '초대 링크 ID',
    `user_id`           BIGINT      NOT NULL COMMENT '링크를 연 회원 ID',
    `status`            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '응답 상태: PENDING, ACCEPTED, DECLINED',
    `created_at`        DATETIME(6) NOT NULL COMMENT '링크를 처음 연 일시',
    `updated_at`        DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_CAPSULE_INVITEES_INVITE_USER` UNIQUE (`capsule_invite_id`, `user_id`)
);

-- 회원별로 대기 중인 초대를 찾는 조회다. 등호 조건인 user_id, status 순이다.
CREATE INDEX `IDX_CAPSULE_INVITEES_USER_ID_STATUS` ON `capsule_invitees` (`user_id`, `status`);

ALTER TABLE `capsule_invitees` ADD CONSTRAINT `FK_CAPSULE_INVITEES_CAPSULE_INVITE` FOREIGN KEY (`capsule_invite_id`) REFERENCES `capsule_invites` (`id`);
ALTER TABLE `capsule_invitees` ADD CONSTRAINT `FK_CAPSULE_INVITEES_USER`           FOREIGN KEY (`user_id`)           REFERENCES `users` (`id`);
