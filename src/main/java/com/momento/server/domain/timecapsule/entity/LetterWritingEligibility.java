package com.momento.server.domain.timecapsule.entity;

/** 편지 작성 가능 여부와 거절 이유. HTTP 응답 및 예외와 독립적인 도메인 판정이다. */
public enum LetterWritingEligibility {
  ALLOWED,
  NOT_ALLOWED,
  CLOSED
}
