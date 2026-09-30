package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteReceivedListResponse;
import com.momento.server.domain.timecapsule.facade.CapsuleInviteFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@RestApiController("/v1/invites")
@RequiredArgsConstructor
public class InviteController implements InviteApi {

  private final CapsuleInviteFacade capsuleInviteFacade;

  @Override
  @GetMapping("/{inviteToken}")
  public CommonResponse<CapsuleInvitePreviewResponse> getInvite(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String inviteToken) {
    return CommonResponse.ok(capsuleInviteFacade.getPreview(inviteToken, principal.getUserId()));
  }

  @Override
  @GetMapping("/received")
  public CommonResponse<CapsuleInviteReceivedListResponse> getReceivedInvites(
      @AuthenticationPrincipal UserPrincipal principal) {
    return CommonResponse.ok(capsuleInviteFacade.getReceived(principal.getUserId()));
  }

  @Override
  @PostMapping("/{inviteToken}/accept")
  public CommonResponse<?> acceptInvite(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String inviteToken) {
    capsuleInviteFacade.accept(inviteToken, principal.getUserId());
    return CommonResponse.ok();
  }

  @Override
  @PostMapping("/{inviteToken}/reject")
  public CommonResponse<?> rejectInvite(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String inviteToken) {
    capsuleInviteFacade.reject(inviteToken, principal.getUserId());
    return CommonResponse.ok();
  }
}
