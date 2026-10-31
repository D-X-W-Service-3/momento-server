-- 초대 링크의 사용 횟수 제한을 없앤다 (이슈 #37).
-- 링크 하나를 여러 명이 열고 수락할 수 있고, 응답은 회원마다 capsule_invitees 에 기록한다(V5).
-- 링크를 끝내는 것은 재생성(REVOKED)뿐이라 몇 번 썼는지 셀 필요가 없다.
ALTER TABLE `capsule_invites` DROP COLUMN `max_uses`;
ALTER TABLE `capsule_invites` DROP COLUMN `used_count`;
