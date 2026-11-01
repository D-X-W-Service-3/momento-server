-- 공유 링크로 받은 아카이브 (이슈 #81).
-- 추억 하나의 공유 링크를 여러 사람이 열 수 있어서, (추억, 받은 회원) 마다 한 행을 둔다.
-- 링크를 열면 PENDING, 저장하면 SAVED, 닫으면 DISMISSED 다. 저장은 복사가 아니라 이 행이 원본 추억을 가리키는 것이라,
-- 원본이 삭제되면 받은 사람 쪽에서도 사라진다.
CREATE TABLE `received_memories` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '받은 아카이브 ID',
    `memory_id`   BIGINT      NOT NULL COMMENT '공유된 원본 추억 ID',
    `receiver_id` BIGINT      NOT NULL COMMENT '링크로 받은 회원 ID',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '받은 상태: PENDING, SAVED, DISMISSED',
    `created_at`  DATETIME(6) NOT NULL COMMENT '처음 받은 일시',
    `updated_at`  DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_RECEIVED_MEMORIES_MEMORY_RECEIVER` UNIQUE (`memory_id`, `receiver_id`)
);

-- 회원별로 받은 아카이브(PENDING) · 저장한 아카이브(SAVED)를 찾는 조회다. 등호 조건인 receiver_id, status 순이다.
CREATE INDEX `IDX_RECEIVED_MEMORIES_RECEIVER_ID_STATUS` ON `received_memories` (`receiver_id`, `status`);

ALTER TABLE `received_memories` ADD CONSTRAINT `FK_RECEIVED_MEMORIES_MEMORY`   FOREIGN KEY (`memory_id`)   REFERENCES `memories` (`id`);
ALTER TABLE `received_memories` ADD CONSTRAINT `FK_RECEIVED_MEMORIES_RECEIVER` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`);
