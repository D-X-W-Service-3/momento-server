package com.momento.server.domain.timecapsule.entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 캡슐의 편지 마감 일시와 개봉 일시. HTTP 응답 및 예외와 독립적인 도메인 판정이다. */
public record CapsuleSchedule(LocalDateTime letterDeadlineAt, LocalDateTime openAt) {

  /** 개봉은 마감보다 이만큼 이상 뒤여야 한다. 마감 직전에 제출된 편지의 AI 이미지를 만들 시간이다. */
  static final Duration MIN_GAP_BETWEEN_DEADLINE_AND_OPEN = Duration.ofHours(1);

  /** 고른 일시를 분 단위로 자른다. 화면에서 분까지만 고르므로 초 이하는 의미가 없다. */
  public static CapsuleSchedule of(LocalDateTime letterDeadlineAt, LocalDateTime openAt) {
    return new CapsuleSchedule(
        letterDeadlineAt.truncatedTo(ChronoUnit.MINUTES), openAt.truncatedTo(ChronoUnit.MINUTES));
  }

  /** 마감이 아직 오지 않았고, 개봉이 마감보다 1시간 이상 뒤인지. 정확히 1시간 뒤는 허용한다. */
  public boolean isValidAt(LocalDateTime now) {
    return letterDeadlineAt.isAfter(now)
        && !openAt.isBefore(letterDeadlineAt.plus(MIN_GAP_BETWEEN_DEADLINE_AND_OPEN));
  }
}
