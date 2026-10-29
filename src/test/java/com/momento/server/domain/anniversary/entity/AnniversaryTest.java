package com.momento.server.domain.anniversary.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnniversaryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  @Nested
  @DisplayName("다음 기념일 날짜")
  class NextDate {

    @Test
    @DisplayName("매년 반복이고 올해 날짜가 아직 오지 않았으면 올해 날짜다")
    void yearlyLaterThisYear() {
      assertThat(yearly(LocalDate.of(2020, 12, 25)).nextDate(TODAY))
          .isEqualTo(LocalDate.of(2026, 12, 25));
    }

    @Test
    @DisplayName("매년 반복이고 오늘이 기념일이면 오늘이다")
    void yearlyToday() {
      assertThat(yearly(LocalDate.of(2020, 10, 29)).nextDate(TODAY)).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("매년 반복이고 올해 날짜가 지났으면 내년 날짜다")
    void yearlyPassedThisYear() {
      assertThat(yearly(LocalDate.of(2020, 10, 28)).nextDate(TODAY))
          .isEqualTo(LocalDate.of(2027, 10, 28));
    }

    @Test
    @DisplayName("매년 반복이고 등록한 날짜가 아직 오지 않았으면 그 날짜가 첫 기념일이다")
    void yearlyFutureDate() {
      assertThat(yearly(LocalDate.of(2030, 5, 1)).nextDate(TODAY))
          .isEqualTo(LocalDate.of(2030, 5, 1));
    }

    @Test
    @DisplayName("2월 29일은 평년에 2월 28일이다")
    void leapDayInCommonYear() {
      assertThat(yearly(LocalDate.of(2024, 2, 29)).nextDate(LocalDate.of(2027, 1, 1)))
          .isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("2월 29일은 윤년에 2월 29일이다")
    void leapDayInLeapYear() {
      assertThat(yearly(LocalDate.of(2024, 2, 29)).nextDate(LocalDate.of(2028, 1, 1)))
          .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("평년 2월 28일이 지나면 다음 2월 29일이 아니라 내년 날짜다")
    void leapDayAfterCommonYearDate() {
      assertThat(yearly(LocalDate.of(2024, 2, 29)).nextDate(LocalDate.of(2026, 3, 1)))
          .isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("반복하지 않으면 지난 날짜도 그대로다")
    void noneKeepsPastDate() {
      LocalDate date = LocalDate.of(2026, 8, 12);

      assertThat(anniversary(date, RepeatType.NONE).nextDate(TODAY)).isEqualTo(date);
    }
  }

  @Nested
  @DisplayName("알림 설정")
  class Notification {

    @Test
    @DisplayName("알림 받기를 켜면 알림 시각과 미리 알림을 그대로 둔다")
    void enabled() {
      Anniversary anniversary = withNotification(true, LocalTime.of(20, 30), 3);

      assertThat(anniversary.isNotificationEnabled()).isTrue();
      assertThat(anniversary.getNotifyTime()).isEqualTo(LocalTime.of(20, 30));
      assertThat(anniversary.getRemindDaysBefore()).isEqualTo(3);
    }

    @Test
    @DisplayName("알림 받기를 끄면 미리 알림은 지우고 알림 시각은 보관한다")
    void disabled() {
      Anniversary anniversary = withNotification(false, LocalTime.of(20, 30), 3);

      assertThat(anniversary.isNotificationEnabled()).isFalse();
      assertThat(anniversary.getNotifyTime()).isEqualTo(LocalTime.of(20, 30));
      assertThat(anniversary.getRemindDaysBefore()).isNull();
    }

    @Test
    @DisplayName("알림 시각이 없으면 09:00 이다")
    void defaultNotifyTime() {
      assertThat(withNotification(true, null, null).getNotifyTime()).isEqualTo(LocalTime.of(9, 0));
    }
  }

  @Test
  @DisplayName("수정은 모든 값을 바꾸고 null 은 지운다")
  void updateReplacesAll() {
    Anniversary anniversary =
        Anniversary.create(
            null,
            "우리 100일",
            LocalDate.of(2026, 8, 12),
            RepeatType.YEARLY,
            "카페 가기",
            true,
            LocalTime.of(20, 30),
            1);

    anniversary.update("첫 만남", LocalDate.of(2026, 5, 4), RepeatType.NONE, null, true, null, null);

    assertThat(anniversary.getTitle()).isEqualTo("첫 만남");
    assertThat(anniversary.getAnniversaryDate()).isEqualTo(LocalDate.of(2026, 5, 4));
    assertThat(anniversary.getRepeatType()).isEqualTo(RepeatType.NONE);
    assertThat(anniversary.getMemo()).isNull();
    assertThat(anniversary.getNotifyTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(anniversary.getRemindDaysBefore()).isNull();
  }

  private static Anniversary yearly(LocalDate date) {
    return anniversary(date, RepeatType.YEARLY);
  }

  private static Anniversary anniversary(LocalDate date, RepeatType repeatType) {
    return Anniversary.create(null, "기념일", date, repeatType, null, false, null, null);
  }

  private static Anniversary withNotification(
      boolean enabled, LocalTime notifyTime, Integer remindDaysBefore) {
    return Anniversary.create(
        null,
        "기념일",
        LocalDate.of(2026, 8, 12),
        RepeatType.YEARLY,
        null,
        enabled,
        notifyTime,
        remindDaysBefore);
  }
}
