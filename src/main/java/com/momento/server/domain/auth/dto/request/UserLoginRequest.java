package com.momento.server.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record UserLoginRequest(
    @Schema(
            description = "프론트에서 Kakao.Auth.authorize 로 받은 인가 코드",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "카카오 인가 코드는 필수입니다.")
        String code) {}
