package com.momento.server.domain.timecapsule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CapsuleScheduleTest {

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
