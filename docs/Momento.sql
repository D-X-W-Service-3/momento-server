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
	`icon_type`	VARCHAR(30)	NOT NULL	COMMENT '캡슐 대표 아이콘',
	`title`	VARCHAR(50)	NOT NULL	COMMENT '타임캡슐 제목',
	`description`	TEXT	NULL	COMMENT '타임캡슐 설명',
	`capsule_type`	VARCHAR(20)	NOT NULL	COMMENT '캡슐 유형: SELF, FRIEND, GROUP',
	`visibility_type`	VARCHAR(30)	NOT NULL	COMMENT '공개 범위: RECIPIENT_AND_AUTHOR, ALL_MEMBERS',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'WRITING'	COMMENT '상태: WRITING, LOCKED, OPENED',
	`open_at`	DATETIME(6)	NOT NULL	COMMENT '공개 예정 일시',
	`letter_deadline_at`	DATETIME(6)	NOT NULL	COMMENT '편지 작성 마감 일시',
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
	`memo`	VARCHAR(200)	NULL	COMMENT '메모',
	`notification_enabled`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '알림 받기 여부',
	`notify_time`	TIME	NOT NULL	DEFAULT '09:00:00'	COMMENT '알림 시각',
	`remind_days_before`	INT	NULL	COMMENT '미리 알림 일수: 1, 3, 5, 7, 10. 알림 받기가 꺼지면 NULL',
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
	`opened_at`	DATETIME(6)	NULL	COMMENT '열린 캡슐을 처음 확인한 일시',
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
	`expires_at`	DATETIME(6)	NULL	COMMENT '초대 링크 만료 일시',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시'
);

-- 초대 링크를 연 회원별 대기 기록 (이슈 #42). 공유 링크 하나를 여러 사람이 열 수 있어 (링크, 회원) 마다 한 행이다.
CREATE TABLE `capsule_invitees` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '초대 대상 ID',
	`capsule_invite_id`	BIGINT	NOT NULL	COMMENT '초대 링크 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '링크를 연 회원 ID',
	`status`	VARCHAR(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT '응답 상태: PENDING, ACCEPTED, DECLINED',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '링크를 처음 연 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시'
);

CREATE TABLE `notifications` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '알림 ID',
	`user_id`	BIGINT	NOT NULL	COMMENT '알림 수신 회원 ID',
	`reference_type`	VARCHAR(30)	NULL	COMMENT '알림이 가리키는 리소스 종류',
	`reference_id`	BIGINT	NULL	COMMENT '알림이 가리키는 리소스 ID',
	`notification_type`	VARCHAR(30)	NOT NULL	COMMENT '알림 유형: CAPSULE_OPENED, CAPSULE_D_DAY, LETTER_DEADLINE, NEW_MEMBER,
MEMORY_REMINDER, ANNIVERSARY_REMINDER, LETTER_MILESTONE, IMAGE_GENERATED (알림 설정과 1:1. CAPSULE_INVITE 없음 — 초대는 링크 수락 방식이라 알림 대상이 없음)',
	`title`	VARCHAR(100)	NOT NULL	COMMENT '알림 제목',
	`content`	VARCHAR(500)	NULL	COMMENT '알림 내용',
	`dedup_key`	VARCHAR(100)	NULL	COMMENT '중복 방지 키. 회원마다 유일하다',
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
	`nickname`	VARCHAR(30)	NOT NULL	UNIQUE	COMMENT '닉네임. 탈퇴 회원도 행이 남아 계속 차지한다',
	`profile_image_url`	VARCHAR(500)	NULL	COMMENT '프로필 이미지 URL',
	`birthday`	DATE	NULL	COMMENT '생일',
	`email`	VARCHAR(100)	NULL	COMMENT '이메일',
	`phone_number`	VARCHAR(20)	NULL	COMMENT '전화번호 (010-1234-5678)',
	`notify_capsule_opened`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '개봉 알림',
	`notify_capsule_d_day`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT 'D-Day 알림',
	`notify_letter_deadline`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '수정 마감 알림',
	`notify_new_member`	BOOLEAN	NOT NULL	DEFAULT TRUE	COMMENT '새 참여자 알림',
	`notify_memory_reminder`	BOOLEAN	NOT NULL	DEFAULT FALSE	COMMENT '추억 다시보기',
	`created_at`	DATETIME(6)	NOT NULL	COMMENT '생성 일시',
	`updated_at`	DATETIME(6)	NOT NULL	COMMENT '수정 일시',
	`deleted_at`	DATETIME(6)	NULL	COMMENT '탈퇴 일시'
);

CREATE TABLE `letters` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT	COMMENT '편지 ID',
	`time_capsule_id`	BIGINT	NOT NULL	COMMENT '타임캡슐 ID',
	`author_id`	BIGINT	NOT NULL	COMMENT '편지 작성자 ID',
	`title`	VARCHAR(50)	NULL	COMMENT '편지 제목. 기존 편지는 NULL',
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

ALTER TABLE `capsule_invitees` ADD CONSTRAINT `PK_CAPSULE_INVITEES` PRIMARY KEY (
	`id`
);

ALTER TABLE `capsule_invitees` ADD CONSTRAINT `UK_CAPSULE_INVITEES_INVITE_USER` UNIQUE (
	`capsule_invite_id`,
	`user_id`
);

ALTER TABLE `notifications` ADD CONSTRAINT `PK_NOTIFICATIONS` PRIMARY KEY (
	`id`
);

-- 알림 목록 조회(cursor keyset)·안읽음 개수 조회가 PK 외 인덱스 없이 회원별 스캔을 하던 것을 막는다 (PR #20 리뷰, 이슈 #23)
ALTER TABLE `notifications` ADD INDEX `IDX_NOTIFICATIONS_USER_ID_ID` (
	`user_id`,
	`id`
);

-- 날짜가 기준인 알림을 스케줄러가 다시 훑어도 한 번만 보낸다 (이슈 #65)
ALTER TABLE `notifications` ADD CONSTRAINT `UK_NOTIFICATIONS_USER_ID_DEDUP_KEY` UNIQUE (
	`user_id`,
	`dedup_key`
);

-- 상태 전이 스케줄러가 주기마다 도는 조회다 (이슈 #30)
ALTER TABLE `time_capsules` ADD INDEX `IDX_TIME_CAPSULES_STATUS_OPEN_AT` (
	`status`,
	`open_at`
);

-- 탭 홈 초대 배너 · 받은 초대 목록이 회원별로 대기 중인 초대를 찾는 조회다 (이슈 #42)
ALTER TABLE `capsule_invitees` ADD INDEX `IDX_CAPSULE_INVITEES_USER_ID_STATUS` (
	`user_id`,
	`status`
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

