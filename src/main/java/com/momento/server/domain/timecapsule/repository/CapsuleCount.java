package com.momento.server.domain.timecapsule.repository;

/** 캡슐 하나에 딸린 행 수. 카드 여러 장의 멤버 수 · 편지 수를 한 번에 셀 때 쓴다. */
public record CapsuleCount(Long capsuleId, long count) {}
