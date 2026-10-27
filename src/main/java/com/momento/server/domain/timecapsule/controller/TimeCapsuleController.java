package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCreateResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleListResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleOpenResponse;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.facade.TimeCapsuleFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestApiController("/v1/time-capsules")
@RequiredArgsConstructor
public class TimeCapsuleController implements TimeCapsuleApi {

  private final TimeCapsuleFacade timeCapsuleFacade;

  @Override
  @PostMapping
  public CommonResponse<TimeCapsuleCreateResponse> createTimeCapsule(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TimeCapsuleCreateRequest request) {
    return CommonResponse.success(
        SuccessCode.CREATED, timeCapsuleFacade.create(principal.getUserId(), request));
  }

  @Override
  @GetMapping
  public CommonResponse<TimeCapsuleListResponse> getTimeCapsules(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) CapsuleStatus status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return CommonResponse.ok(
        timeCapsuleFacade.getList(principal.getUserId(), status, keyword, cursor, size));
  }

  @Override
  @GetMapping("/{capsuleId}")
  public CommonResponse<TimeCapsuleDetailResponse> getTimeCapsule(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long capsuleId) {
    return CommonResponse.ok(timeCapsuleFacade.getDetail(capsuleId, principal.getUserId()));
  }

  @Override
  @PostMapping("/{capsuleId}/open")
  public CommonResponse<TimeCapsuleOpenResponse> openTimeCapsule(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long capsuleId) {
    return CommonResponse.ok(timeCapsuleFacade.open(capsuleId, principal.getUserId()));
  }
}
