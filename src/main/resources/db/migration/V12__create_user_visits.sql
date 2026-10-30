-- 홈 "매일 모멘토에 들러보세요" 의 방문 기록 (이슈 #79).
-- 홈을 조회하면 그날을 방문으로 남긴다. 하루에 여러 번 조회해도 (회원, 날짜) 마다 한 행이다.
-- UNIQUE 가 user_id 로 시작해 회원별 방문 날짜 조회의 인덱스를 겸한다.
CREATE TABLE `user_visits` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '방문 기록 ID',
    `user_id`    BIGINT      NOT NULL COMMENT '방문한 회원 ID',
    `visit_date` DATE        NOT NULL COMMENT '방문한 날짜',
    `created_at` DATETIME(6) NOT NULL COMMENT '그날 처음 방문한 일시',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_USER_VISITS_USER_DATE` UNIQUE (`user_id`, `visit_date`)
);

ALTER TABLE `user_visits` ADD CONSTRAINT `FK_USER_VISITS_USER` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
