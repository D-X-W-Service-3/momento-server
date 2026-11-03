-- 회원이 올린 프로필 사진의 S3 key. 없으면 가입 때 받은 카카오 사진(profile_image_url)을 쓴다.
ALTER TABLE `users` ADD COLUMN `profile_image_key` VARCHAR(255) NULL COMMENT '올린 프로필 사진 S3 key. 없으면 profile_image_url(카카오)을 쓴다' AFTER `profile_image_url`;
