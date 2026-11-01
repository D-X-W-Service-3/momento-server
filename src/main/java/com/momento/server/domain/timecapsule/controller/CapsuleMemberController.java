package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.response.CapsuleMemberListResponse;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.facade.CapsuleMemberFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestApiController("/v1/time-capsules/{capsuleId}/members")
@RequiredArgsConstructor
public class CapsuleMemberController implements CapsuleMemberApi {

  private final CapsuleMemberFacade capsuleMemberFacade;

  @Override
  @GetMapping
  public CommonResponse<CapsuleMemberListResponse> getMembers(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long capsuleId,
      @RequestParam(required = false) MemberRole role) {
    return CommonResponse.ok(
        capsuleMemberFacade.getMembers(capsuleId, principal.getUserId(), role));
  }

  @Override
  @DeleteMapping("/{memberId}")
  public CommonResponse<?> removeMember(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long capsuleId,
      @PathVariable Long memberId) {
    capsuleMemberFacade.remove(capsuleId, principal.getUserId(), memberId);
    return CommonResponse.ok();
  }
}
