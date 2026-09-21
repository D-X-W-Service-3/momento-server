package com.momento.server.domain.letter.controller;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.response.LetterResponse;
import com.momento.server.domain.letter.facade.LetterFacade;
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

@RestApiController("/v1/time-capsules/{capsuleId}/letters")
@RequiredArgsConstructor
public class LetterController implements LetterApi {
  private final LetterFacade letterFacade;

  @Override
  @PostMapping
  public CommonResponse<LetterResponse> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long capsuleId,
      @Valid @RequestBody LetterCreateRequest request) {
    return CommonResponse.success(
        SuccessCode.CREATED, letterFacade.create(capsuleId, principal.getUserId(), request));
  }

  @Override
  @GetMapping("/me")
  public CommonResponse<LetterResponse> getMine(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long capsuleId) {
    return CommonResponse.ok(letterFacade.getMine(capsuleId, principal.getUserId()));
  }
}
