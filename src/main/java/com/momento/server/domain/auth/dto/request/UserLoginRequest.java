package com.momento.server.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record UserLoginRequest(
    @Schema(
            description = "프론트에서 Kakao.Auth.authorize 로 받은 인가 코드",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "카카오 인가 코드는 필수입니다.")
        String code,
    @Schema(
            description = "인가 코드를 받을 때 사용한 Redirect URI. 카카오 콘솔에 등록된 값이어야 하고 문자열까지 같아야 한다.",
            example = "http://localhost:3000/auth/kakao/callback",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Redirect URI 는 필수입니다.")
        String redirectUri) {}
