package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.global.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeCapsuleService {
  private final TimeCapsuleRepository timeCapsuleRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;

  public TimeCapsule getActiveCapsule(Long capsuleId) {
    return timeCapsuleRepository
        .findByIdAndDeletedAtIsNull(capsuleId)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }

  /** 호출한 서비스의 쓰기 트랜잭션에 참여해 후속 저장이 끝날 때까지 캡슐 행 잠금을 유지한다. */
  @Transactional
  public TimeCapsule getActiveCapsuleForUpdate(Long capsuleId) {
    return timeCapsuleRepository
        .findActiveByIdForUpdate(capsuleId)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }

  public CapsuleMember requireActiveMember(Long capsuleId, Long userId) {
    return capsuleMemberRepository
        .findByTimeCapsuleIdAndUserIdAndStatus(capsuleId, userId, MemberStatus.ACTIVE)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }
}
