package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
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

  private List<MemberRole> rolesOf(MemberRole role) {
    return switch (role) {
      case PARTICIPANT -> List.of(MemberRole.OWNER, MemberRole.PARTICIPANT);
      case RECIPIENT -> List.of(MemberRole.RECIPIENT);
      case OWNER -> throw new IllegalArgumentException("방장만 따로 조회하지 않는다: " + role);
    };
  }
}
