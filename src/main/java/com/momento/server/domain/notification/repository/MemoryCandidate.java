package com.momento.server.domain.notification.repository;

import java.time.LocalDate;

/** 추억 다시보기 후보. 지난해 이전의 같은 월 · 일에 남긴 추억이다. */
public record MemoryCandidate(Long memoryId, String title, LocalDate memoryDate, Long userId) {}
