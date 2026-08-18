package com.momento.server.domain.auth.controller;

import com.momento.server.domain.auth.dto.request.UserLoginRequest;
import com.momento.server.domain.auth.dto.response.UserLoginResponse;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Auth", description = "인증 API")
public interface AuthApi {

  @Operation(
      summary = "카카오 로그인",
      description =
          "프론트에서 카카오 SDK 로 받은 액세스 토큰을 넘기면 카카오 사용자 정보를 조회해 가입/로그인 처리하고 서비스 액세스 토큰을 발급한다. 인증 없이 호출한다.")
  @SecurityRequirements
  CommonResponse<UserLoginResponse> loginWithKakao(@Valid UserLoginRequest request);

  @Operation(
      summary = "로그아웃",
      description = "서버는 JWT 를 저장하지 않으므로 상태 변경 없이 성공만 응답한다. 클라이언트가 보관 중인 액세스 토큰을 폐기해야 한다.")
  CommonResponse<?> logout();
}
