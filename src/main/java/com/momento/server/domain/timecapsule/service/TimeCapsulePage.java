package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import java.util.List;
import java.util.Map;

/**
 * 목록 한 페이지의 서비스 결과.
 *
 * @param countsByStatus 검색어를 적용한 상태별 개수. 캡슐이 없는 상태는 들어 있지 않다
 */
public record TimeCapsulePage(
    List<TimeCapsuleSummary> capsules, Map<CapsuleStatus, Long> countsByStatus, boolean hasNext) {}
