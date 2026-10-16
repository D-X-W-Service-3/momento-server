package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteResponse;
import com.momento.server.domain.timecapsule.facade.CapsuleInviteFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestApiController("/v1/time-capsules/{capsuleId}/invites")
@RequiredArgsConstructor
public class CapsuleInviteController implements CapsuleInviteApi {

  private final CapsuleInviteFacade capsuleInviteFacade;

  @Override
  @PostMapping
  public CommonResponse<CapsuleInviteResponse> issueInvite(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long capsuleId,
      @Valid @RequestBody CapsuleInviteCreateRequest request) {
    return CommonResponse.ok(capsuleInviteFacade.issue(capsuleId, principal.getUserId(), request));
  }

  @Override
  @PostMapping("/regenerate")
  public CommonResponse<CapsuleInviteResponse> regenerateInvite(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long capsuleId,
      @Valid @RequestBody CapsuleInviteCreateRequest request) {
    return CommonResponse.success(
        SuccessCode.CREATED,
        capsuleInviteFacade.regenerate(capsuleId, principal.getUserId(), request));
  }

  @Override
  @DeleteMapping("/{inviteId}")
  public CommonResponse<?> cancelInvite(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long capsuleId,
      @PathVariable Long inviteId) {
    capsuleInviteFacade.cancel(capsuleId, principal.getUserId(), inviteId);
    return CommonResponse.ok();
  }
}
