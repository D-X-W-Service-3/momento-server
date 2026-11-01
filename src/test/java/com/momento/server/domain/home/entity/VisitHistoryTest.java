package com.momento.server.domain.home.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VisitHistoryTest {

  /** 2026-10-29 는 목요일이다. */
  private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 29);

  @Nested
  @DisplayName("이번 주")
  class Week {

    @Test
    @DisplayName("오늘이 든 주의 월요일부터 일요일까지 7일이고 들른 날만 표시된다")
    void mondayToSunday() {
      VisitHistory history =
          VisitHistory.of(
              List.of(
                  LocalDate.of(2026, 10, 25), // 지난주 일요일
                  LocalDate.of(2026, 10, 27),
                  THURSDAY));

      assertThat(history.week(THURSDAY))
          .containsExactly(
              new VisitHistory.Day(LocalDate.of(2026, 10, 26), false),
              new VisitHistory.Day(LocalDate.of(2026, 10, 27), true),
              new VisitHistory.Day(LocalDate.of(2026, 10, 28), false),
              new VisitHistory.Day(THURSDAY, true),
              new VisitHistory.Day(LocalDate.of(2026, 10, 30), false),
              new VisitHistory.Day(LocalDate.of(2026, 10, 31), false),
              new VisitHistory.Day(LocalDate.of(2026, 11, 1), false));
    }

    @Test
    @DisplayName("오늘이 월요일이면 오늘부터 시작한다")
    void todayIsMonday() {
      LocalDate monday = LocalDate.of(2026, 10, 26);

      assertThat(VisitHistory.of(List.of(monday)).week(monday))
          .first()
          .isEqualTo(new VisitHistory.Day(monday, true));
    }

    @Test
    @DisplayName("오늘이 일요일이면 그 주 월요일부터 오늘까지다")
    void todayIsSunday() {
      LocalDate sunday = LocalDate.of(2026, 11, 1);

      List<VisitHistory.Day> week = VisitHistory.of(List.of(sunday)).week(sunday);

      assertThat(week.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 26));
      assertThat(week.get(6)).isEqualTo(new VisitHistory.Day(sunday, true));
    }
  }

  @Nested
  @DisplayName("연속 방문 일수")
  class Streak {

    @Test
    @DisplayName("오늘부터 거슬러 올라가 끊기기 전까지 센다")
    void countsBackFromToday() {
      VisitHistory history =
          VisitHistory.of(
              List.of(
                  LocalDate.of(2026, 10, 25),
                  LocalDate.of(2026, 10, 27),
                  LocalDate.of(2026, 10, 28),
                  THURSDAY));

      assertThat(history.streak(THURSDAY)).isEqualTo(3);
    }

    @Test
    @DisplayName("주가 바뀌어도 이어진다")
    void continuesAcrossWeeks() {
      LocalDate monday = LocalDate.of(2026, 11, 2);
      VisitHistory history =
          VisitHistory.of(
              List.of(
                  LocalDate.of(2026, 10, 30),
                  LocalDate.of(2026, 10, 31),
                  LocalDate.of(2026, 11, 1),
                  monday));

      assertThat(history.streak(monday)).isEqualTo(4);
    }

    @Test
    @DisplayName("오늘 들르지 않았으면 어제까지 이어졌어도 0 이다")
    void zeroWithoutToday() {
      VisitHistory history = VisitHistory.of(List.of(LocalDate.of(2026, 10, 28)));

      assertThat(history.streak(THURSDAY)).isZero();
    }
  }
}
