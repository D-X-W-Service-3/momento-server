package com.momento.server.domain.anniversary.controller;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.dto.response.AnniversaryCreateResponse;
import com.momento.server.domain.anniversary.facade.AnniversaryFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestApiController("/v1/anniversaries")
@RequiredArgsConstructor
public class AnniversaryController implements AnniversaryApi {

  private final AnniversaryFacade anniversaryFacade;

  @Override
  @PostMapping
  public CommonResponse<AnniversaryCreateResponse> createAnniversary(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody AnniversaryRequest request) {
    return CommonResponse.success(
        SuccessCode.CREATED, anniversaryFacade.create(principal.getUserId(), request));
  }
}
