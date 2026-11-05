-- 웹 푸시 구독 (이슈 #106).
-- 브라우저가 구독마다 발급하는 endpoint 가 구독을 가리킨다. 한 회원이 기기마다 여러 개를 가질 수 있다.
-- endpoint 가 UNIQUE 라 같은 기기에서 다른 계정으로 다시 구독하면 새 행 없이 소유자를 바꾼다.
-- endpoint 길이 768 은 utf8mb4 인덱스 키 한도(3072바이트)에 들어가는 최대값이다.
CREATE TABLE `push_subscriptions` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '웹 푸시 구독 ID',
    `user_id`    BIGINT       NOT NULL COMMENT '구독한 회원 ID',
    `endpoint`   VARCHAR(768) NOT NULL COMMENT '푸시 서비스 주소. 브라우저가 구독마다 발급한다',
    `p256dh`     VARCHAR(255) NOT NULL COMMENT '페이로드 암호화용 브라우저 공개키 (base64url)',
    `auth`       VARCHAR(255) NOT NULL COMMENT '페이로드 암호화용 인증 비밀값 (base64url)',
    `created_at` DATETIME(6)  NOT NULL COMMENT '처음 구독한 일시',
    `updated_at` DATETIME(6)  NOT NULL COMMENT '마지막으로 다시 구독한 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_PUSH_SUBSCRIPTIONS_ENDPOINT` UNIQUE (`endpoint`)
);

-- 회원의 구독을 모두 찾는 조회(발송 · 탈퇴)는 FK 가 만드는 user_id 인덱스를 쓴다.
ALTER TABLE `push_subscriptions` ADD CONSTRAINT `FK_PUSH_SUBSCRIPTIONS_USER` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
