package com.momento.server.domain.calendar.dto;

/** 통합 검색 정렬. 종류마다 카드에 보이는 날짜(캡슐 개봉일 · 기념일 날짜 · 추억 날짜)가 기준이다. */
public enum CalendarSearchSort {
  /** 최신순. 날짜 내림차순, 같은 날짜면 ID 내림차순이다. */
  LATEST,
  /** 오래된순. 날짜 오름차순, 같은 날짜면 ID 오름차순이다. */
  OLDEST
}
