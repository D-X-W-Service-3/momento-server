package com.momento.server.domain.timecapsule.facade;

import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteReceivedListResponse;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteResponse;
import com.momento.server.domain.timecapsule.service.CapsuleInviteService;
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

  public CapsuleInvitePreviewResponse getPreview(String inviteToken, Long userId) {
    return CapsuleInvitePreviewResponse.of(capsuleInviteService.getPreview(inviteToken, userId));
  }

  public void accept(String inviteToken, Long userId) {
    capsuleInviteService.accept(inviteToken, userId);
  }

  public void reject(String inviteToken, Long userId) {
    capsuleInviteService.reject(inviteToken, userId);
  }

  public CapsuleInviteReceivedListResponse getReceived(Long userId) {
    return CapsuleInviteReceivedListResponse.from(capsuleInviteService.getReceived(userId));
  }
}
