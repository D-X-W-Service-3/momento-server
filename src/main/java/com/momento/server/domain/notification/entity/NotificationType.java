package com.momento.server.domain.notification.entity;

/**
 * 알림 유형. 화면 이동은 이 값 하나로 정해진다({@code referenceId} 가 가리키는 대상은 유형마다 다르다).
 *
 * <p>{@code CAPSULE_INVITE} 는 없다 — 초대는 링크를 공유받은 사람이 직접 수락하는 방식이라, 수락 전에는 알림을 받을 회원이 없다.
 */
public enum NotificationType {
  CAPSULE_RECEIVED,
  LETTER_MILESTONE,
  LETTER_DEADLINE,
  CAPSULE_OPENED,
  IMAGE_GENERATED,
  ANNIVERSARY_REMINDER
}
