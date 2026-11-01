-- Momento 초기 스키마.
--
-- 엔티티에서 Hibernate 가 생성한 DDL 을 기준으로 만들고, 컬럼 주석과 기본값은 설계 문서(docs/Momento.sql)에서 가져왔다.
-- 이 파일이 실제로 적용되는 유일한 스키마 기준이다. 컬럼을 추가하거나 타입을 바꾸려면 이 파일이 아니라 새 V2 를 만든다
-- (이미 적용된 마이그레이션은 체크섬이 기록돼 있어 수정하면 다음 실행이 실패한다).

CREATE TABLE `users` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    `kakao_id`             VARCHAR(100) NOT NULL COMMENT '카카오 회원 고유 ID',
    `nickname`             VARCHAR(30)  NOT NULL COMMENT '닉네임',
    `profile_image_url`    VARCHAR(500) NULL COMMENT '프로필 이미지 URL',
    `notification_enabled` BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '알림 수신 여부',
    `created_at`           DATETIME(6)  NOT NULL COMMENT '생성 일시',
    `updated_at`           DATETIME(6)  NOT NULL COMMENT '수정 일시',
    `deleted_at`           DATETIME(6)  NULL COMMENT '탈퇴 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_USERS_KAKAO_ID` UNIQUE (`kakao_id`)
);

CREATE TABLE `anniversaries` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '기념일 ID',
    `user_id`          BIGINT      NOT NULL COMMENT '기념일을 등록한 회원 ID',
    `title`            VARCHAR(50) NOT NULL COMMENT '기념일 제목',
    `anniversary_date` DATE        NOT NULL COMMENT '기념일 날짜',
    `repeat_type`      VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '반복 유형: NONE, YEARLY',
    `created_at`       DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at`       DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (`id`)
);

CREATE TABLE `time_capsules` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '타임캡슐 ID',
    `creator_id`         BIGINT      NOT NULL COMMENT '타임캡슐 생성자 ID',
    `anniversary_id`     BIGINT      NULL COMMENT '연결된 기념일 ID',
    `title`              VARCHAR(50) NOT NULL COMMENT '타임캡슐 제목',
    `description`        TEXT        NULL COMMENT '타임캡슐 설명',
    `capsule_type`       VARCHAR(20) NOT NULL COMMENT '캡슐 유형: SELF, FRIEND, GROUP',
    `visibility_type`    VARCHAR(30) NOT NULL COMMENT '공개 범위: RECIPIENT_ONLY, RECIPIENT_AND_AUTHOR, ALL_MEMBERS',
    `status`             VARCHAR(20) NOT NULL DEFAULT 'WRITING' COMMENT '상태: WRITING, LOCKED, OPENED',
    `open_at`            DATETIME(6) NOT NULL COMMENT '공개 예정 일시',
    `letter_deadline_at` DATETIME(6) NULL COMMENT '편지 작성 마감 일시',
    `created_at`         DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at`         DATETIME(6) NOT NULL COMMENT '수정 일시',
    `deleted_at`         DATETIME(6) NULL COMMENT '삭제 일시',
    PRIMARY KEY (`id`)
);

CREATE TABLE `capsule_members` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '타임캡슐 참여자 ID',
    `time_capsule_id` BIGINT      NOT NULL COMMENT '타임캡슐 ID',
    `user_id`         BIGINT      NOT NULL COMMENT '회원 ID',
    `role`            VARCHAR(20) NOT NULL COMMENT '참여 역할: OWNER, RECIPIENT, PARTICIPANT',
    `status`          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '참여 상태: ACTIVE, LEFT, REMOVED',
    `joined_at`       DATETIME(6) NULL COMMENT '참여 일시',
    `created_at`      DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at`      DATETIME(6) NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (`id`)
);

CREATE TABLE `capsule_invites` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '초대 링크 ID',
    `time_capsule_id` BIGINT       NOT NULL COMMENT '타임캡슐 ID',
    `inviter_id`      BIGINT       NOT NULL COMMENT '초대 링크 생성자 ID',
    `invite_token`    VARCHAR(100) NOT NULL COMMENT '초대 링크 식별 토큰',
    `target_role`     VARCHAR(20)  NOT NULL DEFAULT 'PARTICIPANT' COMMENT '초대 대상 역할: RECIPIENT, PARTICIPANT',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '초대 링크 상태: ACTIVE, EXPIRED, REVOKED',
    `max_uses`        INT          NULL COMMENT '최대 사용 횟수, NULL 이면 제한 없음',
    `used_count`      INT          NOT NULL DEFAULT 0 COMMENT '현재 사용 횟수',
    `expires_at`      DATETIME(6)  NULL COMMENT '초대 링크 만료 일시',
    `created_at`      DATETIME(6)  NOT NULL COMMENT '생성 일시',
    `updated_at`      DATETIME(6)  NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_CAPSULE_INVITES_INVITE_TOKEN` UNIQUE (`invite_token`)
);

CREATE TABLE `letters` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '편지 ID',
    `time_capsule_id` BIGINT      NOT NULL COMMENT '타임캡슐 ID',
    `author_id`       BIGINT      NOT NULL COMMENT '편지 작성자 ID',
    `content`         TEXT        NOT NULL COMMENT '편지 내용',
    `theme_type`      VARCHAR(20) NULL COMMENT '편지지 테마. 값 목록이 확정되지 않아 문자열이다',
    `status`          VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '작성 상태: DRAFT, SUBMITTED',
    `submitted_at`    DATETIME(6) NULL COMMENT '편지 제출 일시',
    `created_at`      DATETIME(6) NOT NULL COMMENT '생성 일시',
    `updated_at`      DATETIME(6) NOT NULL COMMENT '수정 일시',
    `deleted_at`      DATETIME(6) NULL COMMENT '삭제 일시',
    PRIMARY KEY (`id`)
);

CREATE TABLE `memories` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '추억 기록 ID',
    `user_id`         BIGINT       NOT NULL COMMENT '추억을 등록한 회원 ID',
    `time_capsule_id` BIGINT       NULL COMMENT '원본 타임캡슐 ID, 직접 등록한 추억이면 NULL',
    `title`           VARCHAR(100) NOT NULL COMMENT '추억 제목',
    `content`         TEXT         NULL COMMENT '추억 내용',
    `memory_date`     DATE         NOT NULL COMMENT '추억이 발생한 날짜',
    `visibility_type` VARCHAR(30)  NOT NULL DEFAULT 'PRIVATE' COMMENT '공개 범위: PRIVATE, LINK',
    `share_token`     VARCHAR(255) NULL COMMENT '추억 공유 링크 토큰',
    `created_at`      DATETIME(6)  NOT NULL COMMENT '생성 일시',
    `updated_at`      DATETIME(6)  NOT NULL COMMENT '수정 일시',
    `deleted_at`      DATETIME(6)  NULL COMMENT '삭제 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_MEMORIES_SHARE_TOKEN` UNIQUE (`share_token`)
);

CREATE TABLE `memory_images` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '추억 이미지 ID',
    `memory_id`     BIGINT       NOT NULL COMMENT '추억 기록 ID',
    `image_url`     VARCHAR(500) NOT NULL COMMENT '추억 이미지 URL',
    `display_order` INT          NOT NULL DEFAULT 0 COMMENT '이미지 표시 순서',
    `created_at`    DATETIME(6)  NOT NULL COMMENT '생성 일시',
    PRIMARY KEY (`id`)
);

CREATE TABLE `generated_images` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'AI 생성 이미지 ID',
    `time_capsule_id`   BIGINT       NOT NULL COMMENT '타임캡슐 ID',
    `letter_id`         BIGINT       NULL COMMENT '기준 편지 ID, 그룹 종합 생성이면 NULL',
    `requester_id`      BIGINT       NULL COMMENT '이미지 생성을 요청한 회원 ID',
    `prompt`            TEXT         NULL COMMENT '이미지 생성에 사용된 프롬프트',
    `image_url`         VARCHAR(500) NULL COMMENT '생성 이미지 URL',
    `is_selected`       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '최종 선택 여부',
    `generation_status` VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '생성 상태: PENDING, COMPLETED, FAILED',
    `created_at`        DATETIME(6)  NOT NULL COMMENT '생성 요청 일시',
    `updated_at`        DATETIME(6)  NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (`id`)
);

CREATE TABLE `notifications` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
    `user_id`           BIGINT       NOT NULL COMMENT '알림 수신 회원 ID',
    `reference_type`    VARCHAR(30)  NULL COMMENT '알림이 가리키는 리소스 종류. 지금은 사용하지 않고 확장 대비로 남겨 둔다',
    `reference_id`      BIGINT       NULL COMMENT '알림이 가리키는 리소스 ID',
    `notification_type` VARCHAR(30)  NOT NULL COMMENT '알림 유형: CAPSULE_RECEIVED, LETTER_MILESTONE, LETTER_DEADLINE, CAPSULE_OPENED, IMAGE_GENERATED, ANNIVERSARY_REMINDER',
    `title`             VARCHAR(100) NOT NULL COMMENT '알림 제목',
    `content`           VARCHAR(500) NULL COMMENT '알림 내용',
    `is_read`           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '알림 읽음 여부',
    `read_at`           DATETIME(6)  NULL COMMENT '알림 확인 일시',
    `created_at`        DATETIME(6)  NOT NULL COMMENT '알림 생성 일시',
    PRIMARY KEY (`id`)
);

-- 알림 목록 조회(cursor keyset)와 안 읽은 개수 조회가 회원별로 테이블 전체를 훑지 않도록 한다 (이슈 #23).
CREATE INDEX `IDX_NOTIFICATIONS_USER_ID_ID` ON `notifications` (`user_id`, `id`);

-- 상태 전이 스케줄러가 주기마다 도는 조회다 (이슈 #30). 등호 조건인 status 가 앞, 범위 조건인 open_at 이 뒤다.
CREATE INDEX `IDX_TIME_CAPSULES_STATUS_OPEN_AT` ON `time_capsules` (`status`, `open_at`);

-- 외래키. 소프트 삭제(deleted_at)를 쓰는 테이블이 많아 ON DELETE CASCADE 는 걸지 않고 참조 무결성만 유지한다.
ALTER TABLE `anniversaries`    ADD CONSTRAINT `FK_ANNIVERSARIES_USER`             FOREIGN KEY (`user_id`)         REFERENCES `users` (`id`);
ALTER TABLE `time_capsules`    ADD CONSTRAINT `FK_TIME_CAPSULES_CREATOR`          FOREIGN KEY (`creator_id`)      REFERENCES `users` (`id`);
ALTER TABLE `time_capsules`    ADD CONSTRAINT `FK_TIME_CAPSULES_ANNIVERSARY`      FOREIGN KEY (`anniversary_id`)  REFERENCES `anniversaries` (`id`);
ALTER TABLE `capsule_members`  ADD CONSTRAINT `FK_CAPSULE_MEMBERS_TIME_CAPSULE`   FOREIGN KEY (`time_capsule_id`) REFERENCES `time_capsules` (`id`);
ALTER TABLE `capsule_members`  ADD CONSTRAINT `FK_CAPSULE_MEMBERS_USER`           FOREIGN KEY (`user_id`)         REFERENCES `users` (`id`);
ALTER TABLE `capsule_invites`  ADD CONSTRAINT `FK_CAPSULE_INVITES_TIME_CAPSULE`   FOREIGN KEY (`time_capsule_id`) REFERENCES `time_capsules` (`id`);
ALTER TABLE `capsule_invites`  ADD CONSTRAINT `FK_CAPSULE_INVITES_INVITER`        FOREIGN KEY (`inviter_id`)      REFERENCES `users` (`id`);
ALTER TABLE `letters`          ADD CONSTRAINT `FK_LETTERS_TIME_CAPSULE`           FOREIGN KEY (`time_capsule_id`) REFERENCES `time_capsules` (`id`);
ALTER TABLE `letters`          ADD CONSTRAINT `FK_LETTERS_AUTHOR`                 FOREIGN KEY (`author_id`)       REFERENCES `users` (`id`);
ALTER TABLE `memories`         ADD CONSTRAINT `FK_MEMORIES_USER`                  FOREIGN KEY (`user_id`)         REFERENCES `users` (`id`);
ALTER TABLE `memories`         ADD CONSTRAINT `FK_MEMORIES_TIME_CAPSULE`          FOREIGN KEY (`time_capsule_id`) REFERENCES `time_capsules` (`id`);
ALTER TABLE `memory_images`    ADD CONSTRAINT `FK_MEMORY_IMAGES_MEMORY`           FOREIGN KEY (`memory_id`)       REFERENCES `memories` (`id`);
ALTER TABLE `generated_images` ADD CONSTRAINT `FK_GENERATED_IMAGES_TIME_CAPSULE`  FOREIGN KEY (`time_capsule_id`) REFERENCES `time_capsules` (`id`);
ALTER TABLE `generated_images` ADD CONSTRAINT `FK_GENERATED_IMAGES_LETTER`        FOREIGN KEY (`letter_id`)       REFERENCES `letters` (`id`);
ALTER TABLE `generated_images` ADD CONSTRAINT `FK_GENERATED_IMAGES_REQUESTER`     FOREIGN KEY (`requester_id`)    REFERENCES `users` (`id`);
ALTER TABLE `notifications`    ADD CONSTRAINT `FK_NOTIFICATIONS_USER`             FOREIGN KEY (`user_id`)         REFERENCES `users` (`id`);
