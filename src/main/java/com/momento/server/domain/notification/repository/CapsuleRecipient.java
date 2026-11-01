package com.momento.server.domain.notification.repository;

import java.time.LocalDateTime;

/** 캡슐 알림을 받을 멤버 한 명. 캡슐마다 멤버 수만큼 나온다. */
public record CapsuleRecipient(
    Long capsuleId, String capsuleTitle, LocalDateTime openAt, Long userId) {}
