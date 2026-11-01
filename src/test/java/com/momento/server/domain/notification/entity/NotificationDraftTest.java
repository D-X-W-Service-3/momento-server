package com.momento.server.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDraftTest {

  private static final LocalDate TODAY = LocalDate.of(2027, 12, 5);

  @Test
  @DisplayName("공개 · D-Day 알림은 디자인 문구를 그대로 쓴다")
  void designedTexts() {
    NotificationDraft opened = NotificationDraft.capsuleOpened(1L, 21L);
    NotificationDraft dDay = NotificationDraft.capsuleDDay(1L, 21L, 3, TODAY);

    assertThat(opened.title()).isEqualTo("타임캡슐이 공개되었어요");
    assertThat(opened.content()).isEqualTo("지금바로 확인해보세요");
    assertThat(dDay.title()).isEqualTo("캡슐 개봉 3일 전이에요");
    assertThat(dDay.content()).isEqualTo("어떤 캡슐인지 확인해보세요");
  }

  @Test
  @DisplayName("날짜가 기준인 알림은 키에 보낸 날짜를 넣어 하루 한 번만 가게 한다")
  void dateBasedKeysIncludeToday() {
    assertThat(NotificationDraft.capsuleDDay(1L, 21L, 3, TODAY).dedupKey())
        .isEqualTo("CAPSULE_D_DAY:21:2027-12-05");
    assertThat(NotificationDraft.anniversary(1L, 7L, "결혼기념일", 0, TODAY).dedupKey())
        .isEqualTo("ANNIVERSARY_REMINDER:7:2027-12-05");
    assertThat(NotificationDraft.memoryReminder(1L, 9L, "제주 여행", 2, TODAY).dedupKey())
        .isEqualTo("MEMORY_REMINDER:2027-12-05");
  }

  @Test
  @DisplayName("캡슐마다 한 번뿐인 알림은 키에 날짜가 없고, 새 참여자 알림은 키가 없다")
  void onceKeysAndNoKey() {
    assertThat(NotificationDraft.capsuleOpened(1L, 21L).dedupKey()).isEqualTo("CAPSULE_OPENED:21");
    assertThat(NotificationDraft.letterDeadline(1L, 21L, "우리").dedupKey())
        .isEqualTo("LETTER_DEADLINE:21");
    assertThat(NotificationDraft.newMember(1L, 21L, "우리", "민건").dedupKey()).isNull();
  }

  @Test
  @DisplayName("기념일 알림은 당일과 미리 알림의 제목이 다르다")
  void anniversaryTitles() {
    assertThat(NotificationDraft.anniversary(1L, 7L, "결혼기념일", 0, TODAY).title())
        .isEqualTo("오늘은 기념일이에요");
    assertThat(NotificationDraft.anniversary(1L, 7L, "결혼기념일", 3, TODAY).title())
        .isEqualTo("기념일이 3일 남았어요");
  }

  @Test
  @DisplayName("이동 대상은 유형마다 캡슐 · 추억 · 기념일 ID 다")
  void referenceIds() {
    assertThat(NotificationDraft.newMember(1L, 21L, "우리", "민건").referenceId()).isEqualTo(21L);
    assertThat(NotificationDraft.memoryReminder(1L, 9L, "제주", 1, TODAY).referenceId())
        .isEqualTo(9L);
    assertThat(NotificationDraft.anniversary(1L, 7L, "생일", 1, TODAY).referenceId()).isEqualTo(7L);
  }
}
