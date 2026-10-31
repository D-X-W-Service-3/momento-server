package com.momento.server.domain.notification.entity;

/**
 * 알림 유형. 화면 이동은 이 값 하나로 정해진다({@code referenceId} 가 가리키는 대상은 유형마다 다르다).
 *
 * <p>알림 설정 화면의 토글과 1:1 로 대응한다. 기념일 알림은 기념일마다 켜고 끈다.
 *
 * <p>초대 알림은 없다 — 초대는 링크를 공유받은 사람이 직접 수락하는 방식이라, 수락 전에는 알림을 받을 회원이 없다. 받은 초대는 받은 초대 목록 API 가 보여준다.
 */
public enum NotificationType {
  /** 캡슐이 열렸다. 개봉 알림 설정. referenceId 는 캡슐 ID. */
  CAPSULE_OPENED,

  /** 개봉 7일 전부터 하루 전까지 남은 날짜. D-Day 알림 설정. referenceId 는 캡슐 ID. */
  CAPSULE_D_DAY,

  /** 오늘 편지 수정이 마감된다. 수정 마감 알림 설정. referenceId 는 캡슐 ID (내 편지 화면). */
  LETTER_DEADLINE,

  /** 초대를 수락해 캡슐에 누군가 들어왔다. 새 참여자 알림 설정. referenceId 는 캡슐 ID. */
  NEW_MEMBER,

  /** N년 전 오늘의 추억. 추억 다시보기 설정. referenceId 는 추억 ID. */
  MEMORY_REMINDER,

  /** 기념일 당일 · 미리 알림. 기념일마다 켠다. referenceId 는 기념일 ID. */
  ANNIVERSARY_REMINDER,

  /** 캡슐에 편지가 담겼다. 편지 제출이 생기면 보낸다 — 지금은 편지가 항상 임시 저장이라 보내지 않는다. referenceId 는 캡슐 ID. */
  LETTER_MILESTONE,

  /** AI 이미지 생성이 끝났다. AI 서버 연동이 생기면 보낸다 — 지금은 보내지 않는다. referenceId 는 편지 ID. */
  IMAGE_GENERATED
}
