package com.momento.server.domain.timecapsule.facade;

import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse.CapsuleSummary;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse.MemberPreview;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse.MemberPreviewItem;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteReceivedListResponse;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteReceivedListResponse.Item;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteResponse;
import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.service.CapsuleInviteService;
import com.momento.server.domain.timecapsule.service.CapsuleInviteService.InvitePreviewDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CapsuleInviteFacade {

  private final CapsuleInviteService capsuleInviteService;

  public CapsuleInviteResponse issue(
      Long capsuleId, Long userId, CapsuleInviteCreateRequest request) {
    return CapsuleInviteResponse.from(capsuleInviteService.issue(capsuleId, userId, request));
  }

  public CapsuleInviteResponse regenerate(
      Long capsuleId, Long userId, CapsuleInviteCreateRequest request) {
    return CapsuleInviteResponse.from(capsuleInviteService.regenerate(capsuleId, userId, request));
  }

  public void cancel(Long capsuleId, Long userId, Long inviteId) {
    capsuleInviteService.cancel(capsuleId, userId, inviteId);
  }

  public CapsuleInvitePreviewResponse getPreview(String inviteToken, Long userId) {
    return toPreviewResponse(capsuleInviteService.getPreview(inviteToken, userId));
  }

  public void accept(String inviteToken, Long userId) {
    capsuleInviteService.accept(inviteToken, userId);
  }

  public void reject(String inviteToken, Long userId) {
    capsuleInviteService.reject(inviteToken, userId);
  }

  public CapsuleInviteReceivedListResponse getReceived(Long userId) {
    return CapsuleInviteReceivedListResponse.of(
        capsuleInviteService.getReceived(userId).stream()
            .map(detail -> Item.of(detail.invite(), detail.capsule()))
            .toList());
  }

  private CapsuleInvitePreviewResponse toPreviewResponse(InvitePreviewDetail detail) {
    CapsuleInvite invite = detail.invite();
    TimeCapsule capsule = detail.capsule();

    CapsuleSummary summary =
        new CapsuleSummary(
            capsule.getId(),
            capsule.getTitle(),
            capsule.getDescription(),
            capsule.getCapsuleType(),
            capsule.getStatus(),
            capsule.getOpenAt(),
            detail.memberCount(),
            detail.letterCount(),
            toMemberPreview(detail.participants()),
            toMemberPreview(detail.recipients()));

    return new CapsuleInvitePreviewResponse(
        invite.getTargetRole(), detail.alreadyJoined(), summary);
  }

  private MemberPreview toMemberPreview(CapsuleInviteService.MemberPreview preview) {
    return new MemberPreview(
        preview.count(),
        preview.topMembers().stream()
            .map(member -> MemberPreviewItem.from(member.getUser()))
            .toList());
  }
}
