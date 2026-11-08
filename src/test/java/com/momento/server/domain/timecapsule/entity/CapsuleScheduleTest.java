package com.momento.server.domain.timecapsule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CapsuleScheduleTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Test
  @DisplayName("마감일은 그날 23:59:59 에 잠기고, 개봉일은 그날 00:00 에 열린다")
  void convertsDatesToBoundaryTimes() {
    CapsuleSchedule schedule = CapsuleSchedule.of(TODAY.plusDays(3), TODAY.plusDays(10));

    assertThat(schedule.letterDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 23, 59, 59));
    assertThat(schedule.letterDeadlineAt().getNano()).isZero();
    assertThat(schedule.openAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 0, 0));
  }

  @ParameterizedTest(name = "마감일 오늘 {0}일 뒤, 개봉일 오늘 {1}일 뒤 → {2}")
  @CsvSource({
    // 마감일 하한: 오늘
    "-1, 10, false",
    " 0, 10, true",
    // 마감일 상한: 개봉일 2일 전
    " 8, 10, true",
    " 9, 10, false",
    "10, 10, false",
    "11, 10, false",
    // 오늘 마감이면 개봉일은 모레부터
    " 0,  2, true",
    " 0,  1, false",
    // 개봉일이 지났으면 고를 수 있는 마감일이 없다
    "-3, -1, false"
  })
  @DisplayName("마감일은 오늘부터 개봉일 2일 전까지만 고를 수 있다")
  void deadlineMustBeBetweenTodayAndTwoDaysBeforeOpen(
      int deadlineOffset, int openOffset, boolean expected) {
    CapsuleSchedule schedule =
        CapsuleSchedule.of(TODAY.plusDays(deadlineOffset), TODAY.plusDays(openOffset));

    assertThat(schedule.isValidOn(TODAY)).isEqualTo(expected);
  }

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 14, 0, 30);

  @Test
  @DisplayName("고른 일시는 분 단위로 잘라 저장한다")
  void truncatesToMinutes() {
    CapsuleSchedule schedule =
        CapsuleSchedule.of(
            LocalDateTime.of(2026, 11, 20, 15, 0, 59, 999_000_000),
            LocalDateTime.of(2026, 11, 20, 16, 0, 1));

    assertThat(schedule.letterDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 15, 0));
    assertThat(schedule.openAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 16, 0));
  }

  @ParameterizedTest(name = "마감 = 지금 + {0}분, 개봉 = 마감 + {1}분 → {2}")
  @CsvSource({
    // 마감 하한: 지금보다 뒤 (지금은 14:00:30, 마감은 분 단위)
    "-1, 60, false",
    " 0, 60, false",
    " 1, 60, true",
    // 개봉 하한: 마감 + 1시간
    "60, 59, false",
    "60, 60, true",
    "60, 61, true",
    // 몇 달 뒤 개봉도 그대로 된다
    "60, 129600, true",
    // 개봉이 마감보다 이르거나 같음
    "60,  0, false",
    "60, -60, false"
  })
  @DisplayName("마감은 지금보다 뒤, 개봉은 마감보다 1시간 이상 뒤여야 한다")
  void deadlineAfterNowAndOpenAtLeastOneHourLater(
      int deadlineOffsetMinutes, int gapMinutes, boolean expected) {
    LocalDateTime deadline = NOW.withSecond(0).plusMinutes(deadlineOffsetMinutes);
    CapsuleSchedule schedule = CapsuleSchedule.of(deadline, deadline.plusMinutes(gapMinutes));

    assertThat(schedule.isValidAt(NOW)).isEqualTo(expected);
  }
}
