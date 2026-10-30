package com.momento.server.domain.letter.controller;

import com.momento.server.domain.letter.facade.LetterFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestApiController("/v1/letters")
@RequiredArgsConstructor
public class LetterDeleteController implements LetterDeleteApi {
  private final LetterFacade letterFacade;

  @Override
  @DeleteMapping("/{letterId}")
  public CommonResponse<Void> delete(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long letterId) {
    letterFacade.delete(letterId, principal.getUserId());
    return CommonResponse.success(SuccessCode.OK);
  }
}
