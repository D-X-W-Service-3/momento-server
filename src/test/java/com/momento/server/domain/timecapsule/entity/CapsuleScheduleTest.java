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
}
