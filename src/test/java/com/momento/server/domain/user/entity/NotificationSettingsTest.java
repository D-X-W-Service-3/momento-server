package com.momento.server.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSettingsTest {

  @Test
  @DisplayName("기본값은 캡슐 · 참여자 알림을 켜고 추억 다시보기를 끈다")
  void defaults() {
    NotificationSettings settings = NotificationSettings.defaults();

    assertThat(settings.isCapsuleOpened()).isTrue();
    assertThat(settings.isCapsuleDDay()).isTrue();
    assertThat(settings.isLetterDeadline()).isTrue();
    assertThat(settings.isNewMember()).isTrue();
    assertThat(settings.isMemoryReminder()).isFalse();
  }

  @Test
  @DisplayName("보낸 항목만 바꾸고 null 인 항목은 그대로 둔다")
  void updatesOnlyGivenValues() {
    NotificationSettings settings = NotificationSettings.defaults();

    settings.update(false, null, null, null, true);

    assertThat(settings.isCapsuleOpened()).isFalse();
    assertThat(settings.isCapsuleDDay()).isTrue();
    assertThat(settings.isLetterDeadline()).isTrue();
    assertThat(settings.isNewMember()).isTrue();
    assertThat(settings.isMemoryReminder()).isTrue();
  }

  @Test
  @DisplayName("모두 null 이면 아무것도 바꾸지 않는다")
  void keepsAllWhenNothingGiven() {
    NotificationSettings settings = NotificationSettings.defaults();

    settings.update(null, null, null, null, null);

    assertThat(settings).usingRecursiveComparison().isEqualTo(NotificationSettings.defaults());
  }

  @Test
  @DisplayName("모든 항목을 한 번에 바꿀 수 있다")
  void updatesAll() {
    NotificationSettings settings = NotificationSettings.defaults();

    settings.update(false, false, false, false, true);

    assertThat(settings.isCapsuleOpened()).isFalse();
    assertThat(settings.isCapsuleDDay()).isFalse();
    assertThat(settings.isLetterDeadline()).isFalse();
    assertThat(settings.isNewMember()).isFalse();
    assertThat(settings.isMemoryReminder()).isTrue();
  }
}
