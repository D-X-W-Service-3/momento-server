package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.CapsuleInvitee;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.InviteeStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteeRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.ReceivedInvite;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크 발급 · 재생성과, 링크를 받은 회원의 미리보기 · 수락 · 거절 · 받은 목록.
 *
 * <p>링크 하나는 단톡방에 공유돼 여러 명이 열 수 있다. 그래서 응답은 링크가 아니라 (링크, 회원) 마다 한 행인 {@code capsule_invitees} 에
 * 기록하고, 링크 자체는 수락 · 거절해도 그대로 둔다. 링크를 끝내는 것은 재생성뿐이다.
 *
 * <p>받는 쪽 API 는 다음 경우를 모두 같은 404({@code INVITE_NOT_FOUND})로 돌려준다 — 없는 토큰, 재생성으로 무효가 된 링크, 만료된 링크,
 * 삭제된 캡슐의 링크, 내가 거절한 링크. 구분하면 토큰만으로 캡슐의 상태가 드러난다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapsuleInviteService {

  private final CapsuleInviteRepository capsuleInviteRepository;
  private final CapsuleInviteeRepository capsuleInviteeRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;
  private final TimeCapsuleRepository timeCapsuleRepository;
  private final TimeCapsuleService timeCapsuleService;
  private final UserService userService;
  private final Clock clock;

  /** 해당 역할의 활성 링크가 있으면 그 링크를 그대로 돌려주고(캡슐 상세를 다시 열어도 같은 링크가 보이도록), 없으면 새로 만든다. */
  @Transactional
  public CapsuleInvite issue(Long capsuleId, Long userId, CapsuleInviteCreateRequest request) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember me = timeCapsuleService.requireActiveMember(capsuleId, userId);
    timeCapsuleService.requireManageable(capsule, me);
    validateTargetRole(capsule.getCapsuleType(), request.targetRole());

    return capsuleInviteRepository
        .findByTimeCapsuleIdAndTargetRoleAndStatus(
            capsuleId, request.targetRole(), InviteStatus.ACTIVE)
        .orElseGet(() -> createInvite(capsule, me.getUser(), request.targetRole()));
  }

  /**
   * 해당 역할의 기존 활성 링크는 무효(REVOKED)로 만들고 새 링크를 발급한다. 거절은 그 링크에 대한 기록이라, 거절했던 회원도 새 링크로는 다시 받을 수 있다. 캡슐
   * 행에 쓰기 잠금을 먼저 걸어, 동시에 들어온 두 요청이 함께 기존 링크를 못 보고 활성 링크를 두 개 만드는 걸 막는다.
   */
  @Transactional
  public CapsuleInvite regenerate(Long capsuleId, Long userId, CapsuleInviteCreateRequest request) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember me = timeCapsuleService.requireActiveMember(capsuleId, userId);
    timeCapsuleService.requireManageable(capsule, me);
    validateTargetRole(capsule.getCapsuleType(), request.targetRole());

    capsuleInviteRepository
        .findByTimeCapsuleIdAndTargetRoleAndStatus(
            capsuleId, request.targetRole(), InviteStatus.ACTIVE)
        .ifPresent(CapsuleInvite::revoke);

    return createInvite(capsule, me.getUser(), request.targetRole());
  }

  /**
   * 링크를 연 회원을 대기 중(PENDING)으로 기록하고 초대 화면을 돌려준다. 이미 기록이 있으면 그대로 두고, 이미 이 캡슐의 멤버면 기록하지 않는다(대기 중이
   * 아니다).
   *
   * <p>링크 행을 잠그고 기록 여부를 확인한다. 같은 회원이 링크를 동시에 두 번 열어도 (링크, 회원) UNIQUE 에 걸려 500 이 나지 않게 하기 위해서다.
   */
  @Transactional
  public CapsuleInvitePreview getPreview(String inviteToken, Long userId) {
    CapsuleInvite invite = getUsableInviteForUpdate(inviteToken);
    TimeCapsule capsule = getCapsuleOf(invite);
    Optional<CapsuleInvitee> mine = findMine(invite, userId);
    rejectIfDeclined(mine);

    Long capsuleId = capsule.getId();
    boolean alreadyJoined = isActiveMember(capsuleId, userId);
    if (!alreadyJoined && mine.isEmpty()) {
      capsuleInviteeRepository.save(newInvitee(invite, userId, InviteeStatus.PENDING));
    }

    return new CapsuleInvitePreview(
        invite,
        capsule,
        alreadyJoined,
        capsuleMemberRepository.countByTimeCapsuleIdAndStatus(capsuleId, MemberStatus.ACTIVE),
        timeCapsuleService.countSubmittedLetters(capsuleId),
        timeCapsuleService.participantGroup(capsuleId),
        timeCapsuleService.recipientGroup(capsuleId));
  }

  /**
   * 캡슐에 참여한다. 나갔거나 내보내졌던 회원이면 기존 멤버 행을 되살리고, 내 기록은 ACCEPTED 로 바꾼다. 미리보기를 거치지 않고 바로 수락해도 된다.
   *
   * <p>캡슐 행 → 링크 행 순서로 잠근다. 캡슐을 잠가 같은 회원의 수락이 겹쳐 멤버 행이 두 개 생기는 것과, 내보내기 · 상태 전이와 엇갈리는 것을 막는다. 순서는
   * 재생성과 같아 교착이 생기지 않는다. 링크 엔티티는 잠근 뒤에 처음 읽어, 그사이 재생성으로 무효가 된 링크를 옛 값으로 보지 않는다.
   */
  @Transactional
  public void accept(String inviteToken, Long userId) {
    Long capsuleId =
        capsuleInviteRepository
            .findCapsuleIdByInviteToken(inviteToken)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND));
    TimeCapsule capsule =
        timeCapsuleRepository
            .findActiveByIdForUpdate(capsuleId)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND));
    CapsuleInvite invite = getUsableInviteForUpdate(inviteToken);
    Optional<CapsuleInvitee> mine = findMine(invite, userId);
    rejectIfDeclined(mine);

    LocalDateTime now = LocalDateTime.now(clock);
    if (!capsule.isEditable(now)) {
      throw new ApiException(CapsuleErrorCode.CAPSULE_NOT_EDITABLE);
    }

    capsuleMemberRepository
        .findByTimeCapsuleIdAndUserId(capsuleId, userId)
        .ifPresentOrElse(
            existing -> reactivateOrReject(existing, invite.getTargetRole(), now),
            () ->
                capsuleMemberRepository.save(
                    CapsuleMember.builder()
                        .timeCapsule(capsule)
                        .user(userService.getActiveUser(userId))
                        .role(invite.getTargetRole())
                        .joinedAt(now)
                        .build()));

    mine.ifPresentOrElse(
        CapsuleInvitee::accept,
        () -> capsuleInviteeRepository.save(newInvitee(invite, userId, InviteeStatus.ACCEPTED)));
  }

  /**
   * 내 기록을 DECLINED 로 바꾼다. 미리보기를 거치지 않았으면 기록을 새로 만든다. 이미 거절했으면 그대로 성공한다. 이미 이 캡슐의 멤버면 거절할 대상이 없어 409
   * 다. 이 링크로 수락했다가 나가거나 내보내졌으면 수락 기록을 거절로 덮지 않고 409 다.
   */
  @Transactional
  public void reject(String inviteToken, Long userId) {
    CapsuleInvite invite = getUsableInviteForUpdate(inviteToken);
    TimeCapsule capsule = getCapsuleOf(invite);
    if (isActiveMember(capsule.getId(), userId)) {
      throw new ApiException(CapsuleErrorCode.ALREADY_CAPSULE_MEMBER);
    }

    Optional<CapsuleInvitee> mine = findMine(invite, userId);
    if (mine.filter(CapsuleInvitee::isAccepted).isPresent()) {
      throw new ApiException(CapsuleErrorCode.INVITE_ALREADY_ACCEPTED);
    }
    mine.ifPresentOrElse(
        CapsuleInvitee::decline,
        () -> capsuleInviteeRepository.save(newInvitee(invite, userId, InviteeStatus.DECLINED)));
  }

  /** 내가 열고 아직 응답하지 않은 초대. 탭 홈 배너와 같은 조건이다. */
  public List<ReceivedInvite> getReceived(Long userId) {
    return capsuleInviteeRepository.findPendingFor(userId, LocalDateTime.now(clock));
  }

  /** 재생성으로 무효가 됐거나 만료된 링크는 없는 링크와 똑같이 404 다. */
  private CapsuleInvite getUsableInviteForUpdate(String inviteToken) {
    CapsuleInvite invite =
        capsuleInviteRepository
            .findByInviteTokenForUpdate(inviteToken)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND));
    if (!invite.isUsable(LocalDateTime.now(clock))) {
      throw new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND);
    }
    return invite;
  }

  /** 삭제된 캡슐의 링크는 없는 링크와 똑같이 404 다. */
  private TimeCapsule getCapsuleOf(CapsuleInvite invite) {
    return timeCapsuleRepository
        .findByIdAndDeletedAtIsNull(invite.getTimeCapsule().getId())
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND));
  }

  private Optional<CapsuleInvitee> findMine(CapsuleInvite invite, Long userId) {
    return capsuleInviteeRepository.findByCapsuleInviteIdAndUserId(invite.getId(), userId);
  }

  /** 거절한 링크로는 다시 볼 수도 참여할 수도 없다. 방장이 재생성한 새 링크로는 다시 받을 수 있다. */
  private void rejectIfDeclined(Optional<CapsuleInvitee> mine) {
    if (mine.filter(CapsuleInvitee::isDeclined).isPresent()) {
      throw new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND);
    }
  }

  private boolean isActiveMember(Long capsuleId, Long userId) {
    return capsuleMemberRepository
        .findByTimeCapsuleIdAndUserIdAndStatus(capsuleId, userId, MemberStatus.ACTIVE)
        .isPresent();
  }

  private CapsuleInvitee newInvitee(CapsuleInvite invite, Long userId, InviteeStatus status) {
    return CapsuleInvitee.builder()
        .capsuleInvite(invite)
        .user(userService.getActiveUser(userId))
        .status(status)
        .build();
  }

  private void reactivateOrReject(
      CapsuleMember existing, MemberRole targetRole, LocalDateTime now) {
    if (existing.isActive()) {
      throw new ApiException(CapsuleErrorCode.ALREADY_CAPSULE_MEMBER);
    }
    existing.reactivate(targetRole, now);
  }

  private CapsuleInvite createInvite(TimeCapsule capsule, User inviter, MemberRole targetRole) {
    return capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(inviter)
            .inviteToken(generateToken())
            .targetRole(targetRole)
            .expiresAt(capsule.getLetterDeadlineAt())
            .build());
  }

  /**
   * {@code targetRole} 로 OWNER 는 항상 거절한다. 그 밖엔 캡슐 유형별 규칙이다 — SELF 는 초대 자체가 없고, FRIEND 는 참여자만, GROUP
   * 은 둘 다 가능하다.
   */
  private void validateTargetRole(CapsuleType capsuleType, MemberRole targetRole) {
    if (targetRole == MemberRole.OWNER) {
      throw new ApiException(CapsuleErrorCode.INVALID_INVITE_ROLE);
    }
    boolean allowed =
        switch (capsuleType) {
          case SELF -> false;
          case FRIEND -> targetRole == MemberRole.PARTICIPANT;
          case GROUP -> true;
        };
    if (!allowed) {
      throw new ApiException(CapsuleErrorCode.INVALID_INVITE_ROLE);
    }
  }

  /** 상태를 담지 않는 무작위 값이라 충돌 확률이 무시할 수준이다. 별도 유일성 조회 없이 그대로 저장한다. */
  private String generateToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
