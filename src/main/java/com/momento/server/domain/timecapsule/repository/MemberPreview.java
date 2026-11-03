package com.momento.server.domain.timecapsule.repository;

/**
 * 상세 화면의 참여자 · 수신자 미리보기 한 명.
 *
 * @param profileImageKey 올린 프로필 사진 key. 화면에 보일 주소는 {@code User.profileImageUrl} 로 만든다
 */
public record MemberPreview(
    Long userId, String nickname, String profileImageKey, String profileImageUrl) {}
