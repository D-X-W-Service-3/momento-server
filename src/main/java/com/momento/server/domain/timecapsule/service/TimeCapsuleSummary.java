package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;

/** 캡슐 카드 한 장의 서비스 결과. D-Day 처럼 응답에만 필요한 값은 Facade 가 응답으로 바꿀 때 계산한다. */
public record TimeCapsuleSummary(
    TimeCapsule capsule, boolean openedByMe, long memberCount, long letterCount) {}
