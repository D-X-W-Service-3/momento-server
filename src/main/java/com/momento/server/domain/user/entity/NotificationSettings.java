package com.momento.server.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 종류별 수신 여부. 회원 행에 컬럼으로 저장한다.
 *
 * <p>기념일 알림은 기념일마다 따로 켜므로 여기 없다.
 */
@Embeddable
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings {

  /** 예약한 타임캡슐이 열릴 때. */
  @Column(name = "notify_capsule_opened", nullable = false)
  private boolean capsuleOpened;

  /** 개봉 7일 전부터 남은 날짜. */
  @Column(name = "notify_capsule_d_day", nullable = false)
  private boolean capsuleDDay;

  /** 편지 수정 기간이 끝나기 전. */
  @Column(name = "notify_letter_deadline", nullable = false)
  private boolean letterDeadline;

  /** 타임캡슐에 누군가 참여했을 때. */
  @Column(name = "notify_new_member", nullable = false)
  private boolean newMember;

  /** 지난 추억을 가끔 추천. */
  @Column(name = "notify_memory_reminder", nullable = false)
  private boolean memoryReminder;

  /** 새 회원의 기본값. 추억 다시보기는 추천 알림이라 원할 때 켜게 끈다. 마이그레이션의 컬럼 기본값과 같다. */
  public static NotificationSettings defaults() {
    return new NotificationSettings(true, true, true, true, false);
  }
}
