package com.momento.server.domain.auth.controller;

import com.momento.server.domain.auth.dto.request.UserLoginRequest;
import com.momento.server.domain.auth.dto.response.UserLoginResponse;
import com.momento.server.domain.auth.facade.AuthFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestApiController("/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthFacade authFacade;

  @Override
  @PostMapping("/kakao")
  public CommonResponse<UserLoginResponse> loginWithKakao(
      @Valid @RequestBody UserLoginRequest request) {
    return CommonResponse.ok(authFacade.loginWithKakao(request.code()));
  }

  @Override
  @PostMapping("/logout")
  public CommonResponse<?> logout() {
    return CommonResponse.ok();
  }
}
