package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import java.time.LocalDateTime;

/**
 * 내가 속한 캡슐과, 그 캡슐을 내가 처음 확인한 시각. 목록 카드의 "개봉 가능" / "개봉됨" 배지를 가르려고 멤버 행의 값을 같이 읽는다.
 *
 * @param openedAt 아직 확인하지 않았으면 null
 */
public record MyCapsule(TimeCapsule capsule, LocalDateTime openedAt) {}
