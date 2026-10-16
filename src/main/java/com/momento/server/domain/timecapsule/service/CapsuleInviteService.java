package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CapsuleInviteService {

  /** 미리보기 아바타 스택에 보여줄 상위 인원 수. 나머지는 프론트가 "+N" 으로 뭉친다. */
  private static final int MEMBER_PREVIEW_LIMIT = 5;

  private final CapsuleInviteRepository capsuleInviteRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;
  private final TimeCapsuleService timeCapsuleService;
  private final UserService userService;
  private final Clock clock;

  /**
   * 해당 역할의 활성 링크가 있으면 그 링크를 그대로 돌려주고(캡슐 상세를 다시 열어도 같은 링크가 보이도록), 없으면 새로 만든다. 항상 아직 아무도 선점하지 않은 공유
   * 링크로 만든다 — 대상은 발급 시점이 아니라 누군가 이 링크로 처음 들어올 때 정해진다({@link CapsuleInvite#claim}).
   */
  @Transactional
  public CapsuleInvite issue(Long capsuleId, Long userId, CapsuleInviteCreateRequest request) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    requireOwner(capsuleId, userId);
    validateTargetRole(capsule.getCapsuleType(), request.targetRole());
    requireEditable(capsule);

    return capsuleInviteRepository
        .findActiveSlot(capsuleId, request.targetRole(), null, InviteStatus.ACTIVE)
        .orElseGet(() -> createInvite(capsule, userId, request.targetRole()));
  }

  /**
   * 해당 역할의 기존 활성 링크는 무효(REVOKED)로 만들고 새 링크를 발급한다. 거절은 그 초대(토큰)에 묶이므로, 거절했던 사람도 재생성된 새 링크로는 다시 참여할 수
   * 있다. 캡슐 행에 쓰기 잠금을 먼저 걸어, 동시에 들어온 두 요청이 함께 기존 링크를 못 보고 활성 링크를 두 개 만드는 걸 막는다.
   */
  @Transactional
  public CapsuleInvite regenerate(Long capsuleId, Long userId, CapsuleInviteCreateRequest request) {
    TimeCapsule capsule = timeCapsuleService.getActiveCapsuleForUpdate(capsuleId);
    CapsuleMember requester = requireOwner(capsuleId, userId);
    validateTargetRole(capsule.getCapsuleType(), request.targetRole());
    requireEditable(capsule);

    capsuleInviteRepository
        .findActiveSlot(capsuleId, request.targetRole(), null, InviteStatus.ACTIVE)
        .ifPresent(CapsuleInvite::revoke);

    return capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(requester.getUser())
            .inviteToken(generateToken())
            .targetRole(request.targetRole())
            .expiresAt(expiresAt(capsule))
            .build());
  }

  /**
   * 없거나 다른 캡슐의 초대 ID 는 404 다. 이미 취소·만료된 링크를 다시 취소하는 건(초대 자체는 존재) 에러 없이 성공한다(REST DELETE 의 멱등 관례).
   */
  @Transactional
  public void cancel(Long capsuleId, Long userId, Long inviteId) {
    requireOwner(capsuleId, userId);

    CapsuleInvite invite =
        capsuleInviteRepository
            .findByIdAndTimeCapsuleId(inviteId, capsuleId)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND));

    invite.revoke();
  }

  /**
   * 로그인한 회원이 이 링크를 처음 열면 그 회원에게 고정한다({@link CapsuleInvite#claim}). 이미 다른 회원이 선점한 링크면 존재하지 않는 것과 똑같이
   * 404 다 — "누가 이미 선점했다"는 사실 자체를 드러내지 않는다.
   */
  @Transactional
  public InvitePreviewDetail getPreview(String inviteToken, Long userId) {
    CapsuleInvite invite = claimAndAuthorize(inviteToken, userId);

    TimeCapsule capsule = timeCapsuleService.getActiveCapsule(invite.getTimeCapsule().getId());
    boolean alreadyJoined =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(capsule.getId(), userId, MemberStatus.ACTIVE)
            .isPresent();

    return new InvitePreviewDetail(
        invite,
        capsule,
        alreadyJoined,
        capsuleMemberRepository.countByTimeCapsuleIdAndStatus(capsule.getId(), MemberStatus.ACTIVE),
        timeCapsuleService.countSubmittedLetters(capsule.getId()),
        memberPreview(capsule.getId(), MemberRole.PARTICIPANT),
        memberPreview(capsule.getId(), MemberRole.RECIPIENT));
  }

  /**
   * 링크를 처음 여는 것이면 수락도 그 회원에게 고정하면서 진행한다(미리보기를 거치지 않고 바로 수락을 호출한 경우 대비). 나갔거나 제외됐던 회원이면 기존 행을
   * 재활성화하고, 이미 ACTIVE 참여자면 409 다.
   */
  @Transactional
  public void accept(String inviteToken, Long userId) {
    CapsuleInvite invite = claimAndAuthorize(inviteToken, userId);

    TimeCapsule capsule = timeCapsuleService.getActiveCapsule(invite.getTimeCapsule().getId());
    if (!invite.isUsable(LocalDateTime.now(clock))) {
      throw new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND);
    }
    requireEditable(capsule);

    User user = userService.getActiveUser(userId);
    LocalDateTime now = LocalDateTime.now(clock);

    capsuleMemberRepository
        .findByTimeCapsuleIdAndUserId(capsule.getId(), userId)
        .ifPresentOrElse(
            existing -> reactivateOrReject(existing, invite.getTargetRole(), now),
            () ->
                capsuleMemberRepository.save(
                    CapsuleMember.builder()
                        .timeCapsule(capsule)
                        .user(user)
                        .role(invite.getTargetRole())
                        .joinedAt(now)
                        .build()));

    // 각 링크는 정확히 한 회원에게 선점되므로, 수락도 거절·취소와 마찬가지로 그 링크의 "생애"를 끝낸다 — 안 그러면 수락한 초대가
    // 받은 목록에 계속 남는다(상태를 안 바꾸면 isUsable() 이 계속 true).
    invite.revoke();
  }

  /**
   * 지정형 개념이 없어지고 "선점"으로 바뀌면서, 거절도 마찬가지로 아직 아무도 선점하지 않은 링크면 거절 호출 자체가 선점으로 이어진다 — 그리고 바로 거절 처리된다. 대상
   * 본인이 아니면(이미 다른 회원이 선점) 404. 이미 처리된 초대를 다시 거절해도 에러 없이 성공한다.
   */
  @Transactional
  public void reject(String inviteToken, Long userId) {
    CapsuleInvite invite = claimAndAuthorize(inviteToken, userId);

    invite.revoke();
  }

  /** 나에게 고정된, 아직 응답 대기 중인(수락·거절·만료되지 않은) 초대만 최신순으로 보여준다. */
  public List<InvitePreviewDetail> getReceived(Long userId) {
    return capsuleInviteRepository.findByInviteeIdOrderByCreatedAtDesc(userId).stream()
        .filter(invite -> invite.isUsable(LocalDateTime.now(clock)))
        .map(this::tryBuildReceivedDetail)
        .filter(Objects::nonNull)
        .toList();
  }

  private InvitePreviewDetail tryBuildReceivedDetail(CapsuleInvite invite) {
    try {
      TimeCapsule capsule = timeCapsuleService.getActiveCapsule(invite.getTimeCapsule().getId());
      return new InvitePreviewDetail(invite, capsule, false, 0, 0, null, null);
    } catch (ApiException exception) {
      return null;
    }
  }

  /** 미리보기·거절이 공유하는 "선점하고 본인 확인" 절차. */
  private CapsuleInvite claimAndAuthorize(String inviteToken, Long userId) {
    CapsuleInvite invite =
        capsuleInviteRepository
            .findByInviteTokenForUpdate(inviteToken)
            .orElseThrow(() -> new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND));

    User user = userService.getActiveUser(userId);
    invite.claim(user);
    if (!invite.isTargetedTo(userId)) {
      throw new ApiException(CapsuleErrorCode.INVITE_NOT_FOUND);
    }
    return invite;
  }

  private void reactivateOrReject(
      CapsuleMember existing, MemberRole targetRole, LocalDateTime now) {
    if (existing.isActive()) {
      throw new ApiException(CapsuleErrorCode.ALREADY_CAPSULE_MEMBER);
    }
    existing.reactivate(targetRole, now);
  }

  private CapsuleInvite createInvite(TimeCapsule capsule, Long userId, MemberRole targetRole) {
    User inviter = userService.getActiveUser(userId);
    return capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(inviter)
            .inviteToken(generateToken())
            .targetRole(targetRole)
            .expiresAt(expiresAt(capsule))
            .build());
  }

  private LocalDateTime expiresAt(TimeCapsule capsule) {
    return capsule.getLetterDeadlineAt() != null
        ? capsule.getLetterDeadlineAt()
        : capsule.getOpenAt();
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

  /** 편지 작성이 끝난(LOCKED) 이후나 이미 공개된(OPENED) 캡슐에서는 초대 발급·재생성·수락을 모두 막는다. */
  private void requireEditable(TimeCapsule capsule) {
    if (capsule.getStatus() != CapsuleStatus.WRITING) {
      throw new ApiException(CapsuleErrorCode.CAPSULE_NOT_EDITABLE);
    }
  }

  private CapsuleMember requireOwner(Long capsuleId, Long userId) {
    CapsuleMember member = timeCapsuleService.requireActiveMember(capsuleId, userId);
    if (member.getRole() != MemberRole.OWNER) {
      throw new ApiException(CapsuleErrorCode.CAPSULE_OWNER_ONLY);
    }
    return member;
  }

  /** 상태를 담지 않는 무작위 값이라 충돌 확률이 무시할 수준이다. 별도 유일성 조회 없이 그대로 저장한다. */
  private String generateToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private MemberPreview memberPreview(Long capsuleId, MemberRole role) {
    long count =
        capsuleMemberRepository.countByTimeCapsuleIdAndRoleAndStatus(
            capsuleId, role, MemberStatus.ACTIVE);
    List<CapsuleMember> topMembers =
        capsuleMemberRepository.findByTimeCapsuleIdAndRoleAndStatusOrderByJoinedAtAsc(
            capsuleId, role, MemberStatus.ACTIVE, PageRequest.of(0, MEMBER_PREVIEW_LIMIT));
    return new MemberPreview(count, topMembers);
  }

  /**
   * 미리보기의 "참여자/수신자" 한 역할 몫. {@code topMembers} 는 아바타로 보여줄 상위 인원(최대 {@link #MEMBER_PREVIEW_LIMIT}).
   */
  public record MemberPreview(long count, List<CapsuleMember> topMembers) {}

  /**
   * 미리보기·받은 목록이 공유하는 결과. 받은 목록에서는 {@code alreadyJoined}·인원 수·미리보기까지는 필요 없어 각각 false/0/null 로 둔다 —
   * Facade 가 어떤 응답을 만드는지에 따라 쓰는 필드가 다르다.
   */
  public record InvitePreviewDetail(
      CapsuleInvite invite,
      TimeCapsule capsule,
      boolean alreadyJoined,
      long memberCount,
      long letterCount,
      MemberPreview participants,
      MemberPreview recipients) {}
}
