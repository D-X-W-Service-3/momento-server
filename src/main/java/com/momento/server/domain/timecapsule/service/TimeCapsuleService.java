package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
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
    validateVisibility(request.capsuleType(), request.visibilityType());

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
   * 나에게 쓰는 캡슐은 생성자가 OWNER 로만 들어가 수신자가 없다. {@code RECIPIENT_ONLY} 를 고르면 편지를 볼 수 있는 사람이 0 명이 되고,
   * {@code RECIPIENT_AND_AUTHOR} 는 결과가 {@code ALL_MEMBERS} 와 같다. 고를 의미가 없는데 허용하면 같은 캡슐이 두 값으로 저장돼
   * 통계·필터에서 갈리므로 하나만 받는다. 조용히 바꿔 저장하지 않고 400 으로 거절해 계약을 분명히 한다.
   */
  private void validateVisibility(CapsuleType capsuleType, VisibilityType visibilityType) {
    if (capsuleType == CapsuleType.SELF && visibilityType != VisibilityType.ALL_MEMBERS) {
      throw new ApiException(CapsuleErrorCode.INVALID_SELF_CAPSULE_VISIBILITY);
    }
  }

  /**
   * 참여 중인 회원에게만 상세를 보여준다. 없는 캡슐, 삭제된 캡슐, 참여하지 않았거나 나간 캡슐을 모두 같은 404 로 돌려준다 — 구분하면 순번 ID 를 대입하는 것만으로
   * 캡슐 존재 여부가 드러난다.
   */
  public TimeCapsuleDetail getDetail(Long capsuleId, Long userId) {
    TimeCapsule capsule =
        timeCapsuleRepository
            .findByIdAndDeletedAtIsNull(capsuleId)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));

    CapsuleMember me =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(capsuleId, userId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));

    long memberCount =
        capsuleMemberRepository.countByTimeCapsuleIdAndStatus(capsuleId, MemberStatus.ACTIVE);
    long letterCount =
        timeCapsuleRepository.countLettersByStatus(capsuleId, LetterStatus.SUBMITTED);

    return new TimeCapsuleDetail(capsule, me.getRole(), memberCount, letterCount);
  }
}
