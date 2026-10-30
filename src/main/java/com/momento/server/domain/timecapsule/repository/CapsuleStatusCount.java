package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;

/** 상태별 캡슐 수. 목록 화면의 탭별 개수에 쓴다. */
public record CapsuleStatusCount(CapsuleStatus status, long count) {}
