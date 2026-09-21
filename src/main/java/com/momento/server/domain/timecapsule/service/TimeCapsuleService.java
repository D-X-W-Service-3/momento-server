package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
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

  /**
   * 캡슐과 생성자의 OWNER 참여 행을 만든다. {@code creator_id} 와 OWNER 행은 같은 사실을 두 곳에 담는데, 소유권 이전이 없어 둘이 어긋날 수 있는
   * 시점은 생성 순간뿐이다. 그래서 반드시 이 메서드 하나의 트랜잭션 안에서 함께 만든다.
   */
  @Transactional
  public TimeCapsule create(User creator, TimeCapsuleCreateRequest request) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(creator)
                .title(request.title())
                .description(request.description())
                .capsuleType(request.capsuleType())
                .visibilityType(request.visibilityType())
                .openAt(request.openAt())
                .build());

    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(creator)
            .role(MemberRole.OWNER)
            .joinedAt(capsule.getCreatedAt())
            .build());

    return capsule;
  }

  /**
   * 참여 중인 회원에게만 상세를 보여준다. 없는 캡슐, 삭제된 캡슐, 참여하지 않았거나 나간 캡슐을 모두 같은 404 로 돌려준다 — 구분하면 순번 ID 를 대입하는 것만으로
   * 캡슐 존재 여부가 드러난다.
   */
  public TimeCapsuleDetail getDetail(Long capsuleId, Long userId) {
    TimeCapsule capsule = getActiveCapsule(capsuleId);

    CapsuleMember me = requireActiveMember(capsuleId, userId);

    long memberCount =
        capsuleMemberRepository.countByTimeCapsuleIdAndStatus(capsuleId, MemberStatus.ACTIVE);
    long letterCount =
        timeCapsuleRepository.countLettersByStatus(capsuleId, LetterStatus.SUBMITTED);

    return new TimeCapsuleDetail(capsule, me.getRole(), memberCount, letterCount);
  }

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
