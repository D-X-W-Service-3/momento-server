package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleResponse;
import com.momento.server.domain.timecapsule.facade.TimeCapsuleFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestApiController("/v1/time-capsules")
@RequiredArgsConstructor
public class TimeCapsuleController implements TimeCapsuleApi {

  private final TimeCapsuleFacade timeCapsuleFacade;

  @Override
  @PostMapping
  public CommonResponse<TimeCapsuleResponse> createTimeCapsule(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TimeCapsuleCreateRequest request) {
    return CommonResponse.success(
        SuccessCode.CREATED, timeCapsuleFacade.create(principal.getUserId(), request));
  }
}
