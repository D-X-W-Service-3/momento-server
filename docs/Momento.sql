-- Momento 스키마 설계 문서.
--
-- ⚠️ 이 파일은 실행되지 않는다. 실제로 DB 를 만드는 것은 src/main/resources/db/migration/ 의
--    Flyway 마이그레이션이고, 스키마의 기준은 그쪽이다. 이 파일은 전체 구조를 한눈에 보고
--    설계를 논의하기 위한 문서다. 스키마를 바꿀 때는 엔티티와 새 마이그레이션을 고치고,
--    이 문서도 같이 갱신한다.

CREATE TABLE `generated_images` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT 'AI 생성 이미지 ID',
	`time_capsule_id`	BIGINT	NOT NULL	COMMENT '타임캡슐 ID',
	`letter_id`	BIGINT	NULL	COMMENT '기준 편지 ID, 그룹 종합 생성이면 NULL',
	`requester_id`	BIGINT	NULL	COMMENT '이미지 생성을 요청한 회원 ID',
	`prompt`	TEXT	NULL	COMMENT '이미지 생성에 사용된 프롬프트',
	`image_url`	VARCHAR(500)	NULL	COMMENT '생성 이미지 URL',
	`is_selected`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '최종 선택 여부',
	`generation_status`	VARCHAR(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT '생성 상태: PENDING, COMPLETED, FAILED',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 요청 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시'
);

CREATE TABLE `memory_images` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '추억 이미지 ID',
	`memory_id`	BIGINT	NOT NULL	COMMENT '추억 기록 ID',
	`image_url`	VARCHAR(500)	NOT NULL	COMMENT '추억 이미지 URL',
	`display_order`	INT	NOT NULL	DEFAULT 0	COMMENT '이미지 표시 순서',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시'
);

CREATE TABLE `time_capsules` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '타임캡슐 ID',
	`creator_id`	BIGINT	NOT NULL	COMMENT '타임캡슐 생성자 ID',
	`anniversary_id`	BIGINT	NULL	COMMENT '연결된 기념일 ID',
	`title`	VARCHAR(50)	NOT NULL	COMMENT '타임캡슐 제목',
	`description`	TEXT	NULL	COMMENT '타임캡슐 설명',
	`capsule_type`	VARCHAR(20)	NOT NULL	COMMENT '캡슐 유형: SELF, FRIEND, GROUP',
	`visibility_type`	VARCHAR(30)	NOT NULL	COMMENT '공개 범위: RECIPIENT_ONLY, RECIPIENT_AND_AUTHOR, ALL_MEMBERS',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'WRITING'	COMMENT '상태: WRITING, LOCKED, OPENED',
	`open_at`	DATETIME(6)	NOT NULL	COMMENT '공개 예정 일시',
	`letter_deadline_at`	DATETIME(6)	NULL	COMMENT '편지 작성 마감 일시',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '삭제 일시'
);

CREATE TABLE `anniversaries` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '기념일 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '기념일을 등록한 회원 ID',
	`title`	VARCHAR(50)	NOT NULL	COMMENT '기념일 제목',
	`anniversary_date`	DATE	NOT NULL	COMMENT '기념일 날짜',
	`repeat_type`	VARCHAR(20)	NOT NULL	DEFAULT 'NONE'	COMMENT '반복 유형: NONE, YEARLY',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시'
);

CREATE TABLE `capsule_members` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '타임캡슐 참여자 ID',
	`time_capsule_id`	BIGINT	NOT NULL	COMMENT '타임캡슐 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '회원 ID',
	`role`	VARCHAR(20)	NOT NULL	COMMENT '참여 역할: OWNER, RECIPIENT, PARTICIPANT',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '참여 상태: ACTIVE, LEFT, REMOVED',
	`joined_at`	DATETIME(6)	NULL	COMMENT '참여 일시',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시'
);

CREATE TABLE `capsule_invites` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '초대 링크 ID',
	`time_capsule_id`	BIGINT	NOT NULL	COMMENT '타임캡슐 ID',
	`inviter_id`	BIGINT	NOT NULL	COMMENT '초대 링크 생성자 ID',
	`invite_token`	VARCHAR(100)	NOT NULL	UNIQUE	COMMENT '초대 링크 식별 토큰',
	`target_role`	VARCHAR(20)	NOT NULL	DEFAULT 'PARTICIPANT'	COMMENT '초대 대상 역할: RECIPIENT, PARTICIPANT',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '초대 링크 상태: ACTIVE, EXPIRED, REVOKED',
	`max_uses`	INT	NULL	COMMENT '최대 사용 횟수, NULL이면 제한 없음',
	`used_count`	INT	NOT NULL	DEFAULT 0	COMMENT '현재 사용 횟수',
	`expires_at`	DATETIME(6)	NULL	COMMENT '초대 링크 만료 일시',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시'
);

CREATE TABLE `notifications` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '알림 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '알림 수신 회원 ID',
	`reference_type`	VARCHAR(30)	NULL	COMMENT '알림이 가리키는 리소스 종류',
	`reference_id`	BIGINT	NULL	COMMENT '알림이 가리키는 리소스 ID',
	`notification_type`	VARCHAR(30)	NOT NULL	COMMENT '알림 유형: CAPSULE_RECEIVED, LETTER_MILESTONE, LETTER_DEADLINE,
CAPSULE_OPENED, IMAGE_GENERATED, ANNIVERSARY_REMINDER (CAPSULE_INVITE 없음 — 초대는 링크 수락 방식이라 알림 대상이 없음)',
	`title`	VARCHAR(100)	NOT NULL	COMMENT '알림 제목',
	`content`	VARCHAR(500)	NULL	COMMENT '알림 내용',
	`is_read`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '알림 읽음 여부',
	`read_at`	DATETIME(6)	NULL	COMMENT '알림 확인 일시',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '알림 생성 일시'
);

CREATE TABLE `memories` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '추억 기록 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '추억을 등록한 회원 ID',
	`time_capsule_id`	BIGINT	NULL	COMMENT '원본 타임캡슐 ID, 직접 등록한 추억이면 NULL',
	`title`	VARCHAR(100)	NOT NULL	COMMENT '추억 제목',
	`content`	TEXT	NULL	COMMENT '추억 내용',
	`memory_date`	DATE	NOT NULL	COMMENT '추억이 발생한 날짜',
	`visibility_type`	VARCHAR(30)	NOT NULL	DEFAULT 'PRIVATE'	COMMENT '공개 범위: PRIVATE, LINK',
	`share_token`	VARCHAR(255)	NULL	UNIQUE	COMMENT '추억 공유 링크 토큰',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '삭제 일시'
);

CREATE TABLE `users` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '회원 ID',
	`kakao_id`	VARCHAR(100)	NOT NULL	UNIQUE	COMMENT '카카오 회원 고유 ID',
	`nickname`	VARCHAR(30)	NOT NULL	COMMENT '닉네임',
	`profile_image_url`	VARCHAR(500)	NULL	COMMENT '프로필 이미지 URL',
	`notification_enabled`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '알림 수신 여부',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '탈퇴 일시'
);

CREATE TABLE `letters` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '편지 ID',
	`time_capsule_id`	BIGINT	NOT NULL	COMMENT '타임캡슐 ID',
	`author_id`	BIGINT	NOT NULL	COMMENT '편지 작성자 ID',
	`content`	TEXT	NOT NULL	COMMENT '편지 내용',
	`theme_type`	VARCHAR(20)	NULL,
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'DRAFT'	COMMENT '작성 상태: DRAFT, SUBMITTED',
	`submitted_at`	DATETIME(6)	NULL	COMMENT '편지 제출 일시',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '삭제 일시'
);

ALTER TABLE `generated_images` ADD CONSTRAINT `PK_GENERATED_IMAGES` PRIMARY KEY (
	`id`
);

ALTER TABLE `memory_images` ADD CONSTRAINT `PK_MEMORY_IMAGES` PRIMARY KEY (
	`id`
);

ALTER TABLE `time_capsules` ADD CONSTRAINT `PK_TIME_CAPSULES` PRIMARY KEY (
	`id`
);

ALTER TABLE `anniversaries` ADD CONSTRAINT `PK_ANNIVERSARIES` PRIMARY KEY (
	`id`
);

ALTER TABLE `capsule_members` ADD CONSTRAINT `PK_CAPSULE_MEMBERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `capsule_invites` ADD CONSTRAINT `PK_CAPSULE_INVITES` PRIMARY KEY (
	`id`
);

ALTER TABLE `notifications` ADD CONSTRAINT `PK_NOTIFICATIONS` PRIMARY KEY (
	`id`
);

-- 알림 목록 조회(cursor keyset)·안읽음 개수 조회가 PK 외 인덱스 없이 회원별 스캔을 하던 것을 막는다 (PR #20 리뷰, 이슈 #23)
ALTER TABLE `notifications` ADD INDEX `IDX_NOTIFICATIONS_USER_ID_ID` (
	`user_id`,
	`id`
);

ALTER TABLE `memories` ADD CONSTRAINT `PK_MEMORIES` PRIMARY KEY (
	`id`
);

ALTER TABLE `users` ADD CONSTRAINT `PK_USERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `letters` ADD CONSTRAINT `PK_LETTERS` PRIMARY KEY (
	`id`
);

