package com.momento.server.domain.home.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** 한 회원이 들른 날들. 홈의 "매일 모멘토에 들러보세요" 를 그린다. */
public record VisitHistory(Set<LocalDate> visitDates) {

  private static final int DAYS_IN_WEEK = 7;

  public VisitHistory {
    visitDates = Set.copyOf(visitDates);
  }

  public static VisitHistory of(Collection<LocalDate> visitDates) {
    return new VisitHistory(Set.copyOf(visitDates));
  }

  /** 오늘이 든 주의 월요일부터 일요일까지. 오늘 뒤의 날은 아직 오지 않아 들르지 않은 날이다. */
  public List<Day> week(LocalDate today) {
    LocalDate monday = today.with(DayOfWeek.MONDAY);
    return IntStream.range(0, DAYS_IN_WEEK)
        .mapToObj(monday::plusDays)
        .map(date -> new Day(date, visitDates.contains(date)))
        .toList();
  }

  /** 오늘부터 하루씩 거슬러 올라가 들르지 않은 날이 나오기 전까지의 일수. 주가 바뀌어도 이어진다. 오늘 들르지 않았으면 0 이다. */
  public int streak(LocalDate today) {
    int streak = 0;
    for (LocalDate day = today; visitDates.contains(day); day = day.minusDays(1)) {
      streak++;
    }
    return streak;
  }

  public record Day(LocalDate date, boolean visited) {}
}
