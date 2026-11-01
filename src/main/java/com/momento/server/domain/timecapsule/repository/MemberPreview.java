package com.momento.server.domain.timecapsule.repository;

/** 상세 화면의 참여자 · 수신자 미리보기 한 명. */
public record MemberPreview(Long userId, String nickname, String profileImageUrl) {}
