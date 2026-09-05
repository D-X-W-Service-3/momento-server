package com.momento.server.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record UserLoginResponse(
    @Schema(description = "이후 요청의 Authorization 헤더에 Bearer 로 담을 서비스 액세스 토큰") String accessToken,
    @Schema(description = "이번 로그인으로 새로 가입한 회원인지 여부", example = "false") @JsonProperty("isNewUser")
        boolean isNewUser) {

  public static UserLoginResponse of(String accessToken, boolean isNewUser) {
    return new UserLoginResponse(accessToken, isNewUser);
  }
}
