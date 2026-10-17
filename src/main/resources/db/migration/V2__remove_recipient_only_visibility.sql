-- 공개 범위 RECIPIENT_ONLY(수신자만 편지를 본다)를 없앤다 (이슈 #40).
-- 확정 디자인의 "지정한 사람만 캡슐을 열어볼게요" 는 수신자가 아닌 참여자도 자기 편지는 보는 동작이라 RECIPIENT_AND_AUTHOR 와 같다.
-- 남는 값은 RECIPIENT_AND_AUTHOR, ALL_MEMBERS 둘이다.
UPDATE `time_capsules`
SET `visibility_type` = 'RECIPIENT_AND_AUTHOR'
WHERE `visibility_type` = 'RECIPIENT_ONLY';
