package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.letter.repository.LetterRepository;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 참여자 관리 · 수신자 관리 화면. 캡슐 조회와 방장 판정은 {@link TimeCapsuleService} 의 것을 그대로 쓴다. */
@Service
@RequiredArgsConstructor
public class CapsuleMemberService {

  private final TimeCapsuleService timeCapsuleService;
  private final CapsuleMemberRepository capsuleMemberRepository;
  private final LetterRepository letterRepository;
  private final Clock clock;

  /**
   * 역할별 멤버 전체. 참여 중인 멤버라면 역할과 관계없이 볼 수 있다. 캡슐 · 내 멤버 · 목록 세 조회를 같은 시점의 데이터로 읽도록 트랜잭션으로 묶는다.
   *
   * @param role {@code PARTICIPANT} 또는 {@code RECIPIENT}. {@code PARTICIPANT} 는 방장까지 읽는다
   */
  @Transactional(readOnly = true)
  public CapsuleMemberList getMembers(Long capsuleId, Long userId, MemberRole role) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsule(capsuleId);
    CapsuleMember me = timeCapsuleService.requireActiveMember(capsuleId, userId);

    List<CapsuleMember> members =
        capsuleMemberRepository.findActiveWithUser(capsuleId, rolesOf(role));
    return new CapsuleMemberList(role, members, timeCapsuleService.canManage(capsule, me));
  }

  /**
   * 방장이 멤버를 내보낸다. 멤버 행은 {@code REMOVED} 로 남기고, 그 사람이 이 캡슐에 쓴 편지(임시 저장 포함)를 소프트 삭제한다.
   *
   * <p>편지 작성과 같은 캡슐 행 잠금을 먼저 얻는다. 잠그지 않으면 편지를 지운 뒤 같은 사람의 편지 작성이 커밋돼, 내보낸 사람의 편지가 캡슐에 남는다. 잠금을 얻으면
   * 편지 작성이 먼저 끝나 그 편지까지 지우거나, 내보내기가 먼저 끝나 편지 작성이 404 가 된다.
   *
   * <p>판정은 방장 여부 · 편집 가능 여부를 대상보다 먼저 본다. 방장이 아닌 사람이 멤버 ID 를 대입해 대상의 존재를 알아내지 못하게 하기 위해서다.
   */
  @Transactional
  public void remove(Long capsuleId, Long userId, Long memberId) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember me = timeCapsuleService.requireActiveMember(capsuleId, userId);
    timeCapsuleService.requireManageable(capsule, me);
    // 방장은 한 명뿐이라 대상이 방장이면 곧 나 자신이다. 대상을 읽지 않고 판정한다.
    if (me.getId().equals(memberId)) {
      throw new ApiException(CapsuleErrorCode.CANNOT_REMOVE_OWNER);
    }

    CapsuleMember target =
        capsuleMemberRepository
            .findByIdAndTimeCapsuleIdAndStatus(memberId, capsuleId, MemberStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_MEMBER_NOT_FOUND));
    target.remove();
    letterRepository.softDeleteByAuthor(
        capsuleId, target.getUser().getId(), LocalDateTime.now(clock));
  }

  private List<MemberRole> rolesOf(MemberRole role) {
    return switch (role) {
      case PARTICIPANT -> List.of(MemberRole.OWNER, MemberRole.PARTICIPANT);
      case RECIPIENT -> List.of(MemberRole.RECIPIENT);
      case OWNER -> throw new IllegalArgumentException("방장만 따로 조회하지 않는다: " + role);
    };
  }
}
