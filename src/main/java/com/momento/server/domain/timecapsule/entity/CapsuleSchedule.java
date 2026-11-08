package com.momento.server.domain.timecapsule.entity;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * 날짜로 고른 편지 수정 마감일 · 개봉일을 저장할 일시로 바꾼 값. 마감일 하루가 끝나는 23:59:59 에 잠기고, 개봉일이 시작하는 00:00 에 열린다. HTTP 응답
 * 및 예외와 독립적인 도메인 판정이다.
 */
public record CapsuleSchedule(LocalDateTime letterDeadlineAt, LocalDateTime openAt) {

  /** {@link LocalTime#MAX} 는 나노초까지 들어가 DATETIME(6) 에 저장할 때 다음 날 00:00 으로 반올림될 수 있어 초 단위로 끊는다. */
  private static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59, 59);

  /** 마감 다음 날 바로 열리면 잠긴 상태가 1초뿐이라, 마감일과 개봉일 사이에 하루 이상을 둔다. */
  private static final int MIN_DAYS_BETWEEN_DEADLINE_AND_OPEN = 2;

  /** 개봉은 마감보다 이만큼 이상 뒤여야 한다. 마감 직전에 제출된 편지의 AI 이미지를 만들 시간이다. */
  static final Duration MIN_GAP_BETWEEN_DEADLINE_AND_OPEN = Duration.ofHours(1);

  public static CapsuleSchedule of(LocalDate letterDeadlineDate, LocalDate openDate) {
    return new CapsuleSchedule(letterDeadlineDate.atTime(DEADLINE_TIME), openDate.atStartOfDay());
  }

  /** 고른 일시를 분 단위로 자른다. 화면에서 분까지만 고르므로 초 이하는 의미가 없다. */
  public static CapsuleSchedule of(LocalDateTime letterDeadlineAt, LocalDateTime openAt) {
    return new CapsuleSchedule(
        letterDeadlineAt.truncatedTo(ChronoUnit.MINUTES), openAt.truncatedTo(ChronoUnit.MINUTES));
  }

  /** 마감일이 오늘부터 개봉일 2일 전 사이인지. 오늘을 마감일로 고르면 그날 밤까지 쓸 수 있다. */
  public boolean isValidOn(LocalDate today) {
    LocalDate deadlineDate = letterDeadlineAt.toLocalDate();
    LocalDate lastDeadlineDate = openAt.toLocalDate().minusDays(MIN_DAYS_BETWEEN_DEADLINE_AND_OPEN);
    return !deadlineDate.isBefore(today) && !deadlineDate.isAfter(lastDeadlineDate);
  }

  /** 마감이 아직 오지 않았고, 개봉이 마감보다 1시간 이상 뒤인지. 정확히 1시간 뒤는 허용한다. */
  public boolean isValidAt(LocalDateTime now) {
    return letterDeadlineAt.isAfter(now)
        && !openAt.isBefore(letterDeadlineAt.plus(MIN_GAP_BETWEEN_DEADLINE_AND_OPEN));
  }
}
