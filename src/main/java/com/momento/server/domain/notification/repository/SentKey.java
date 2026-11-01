package com.momento.server.domain.notification.repository;

/** 이미 보낸 알림의 (회원, 중복 방지 키). */
public record SentKey(Long userId, String dedupKey) {}
