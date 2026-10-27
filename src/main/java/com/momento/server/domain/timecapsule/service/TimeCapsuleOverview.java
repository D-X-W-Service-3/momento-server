package com.momento.server.domain.timecapsule.service;

import java.util.List;

/** 탭 홈의 서비스 결과. 섹션은 각각 정렬과 개수 제한이 끝난 상태다. */
public record TimeCapsuleOverview(
    boolean hasPendingInvite,
    List<TimeCapsuleSummary> upcoming,
    List<TimeCapsuleSummary> writing,
    List<TimeCapsuleSummary> locked,
    List<TimeCapsuleSummary> opened) {}
